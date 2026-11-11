package com.stayhub.integracion;

import com.stayhub.canalesexternos.messaging.ConsumidorSincronizacion;
import com.stayhub.canalesexternos.messaging.ProcesadorSincronizacion;
import com.stayhub.canalesexternos.messaging.PublicadorSincronizacion;
import com.stayhub.canalesexternos.messaging.SincronizacionIdempotente;
import com.stayhub.canalesexternos.messaging.SolicitudSincronizacion;
import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.messaging.ConsumidorEventoPago;
import com.stayhub.notificaciones.messaging.ConsumidorEventoPassword;
import com.stayhub.notificaciones.messaging.ConsumidorEventoUsuarioRegistrado;
import com.stayhub.notificaciones.messaging.ConsumidorSolicitudNotificacionAsincrona;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import com.stayhub.pagos.messaging.PublicadorEventoPago;
import com.stayhub.servicioDeOverbooking.messaging.PublicadorSolicitudNotificacionAsincrona;
import com.stayhub.servicioDeOverbooking.messaging.SolicitudNotificacionAsincrona;
import com.stayhub.usuarios.messaging.EventoPassword;
import com.stayhub.usuarios.messaging.EventoUsuarioRegistrado;
import com.stayhub.usuarios.messaging.PublicadorEventoPassword;
import com.stayhub.usuarios.messaging.PublicadorEventoUsuario;
import com.stayhub.usuarios.messaging.TipoEventoPassword;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.Queue;
import jakarta.jms.Topic;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import static com.stayhub.integracion.EntornoIntegracion.contar;
import static com.stayhub.integracion.EntornoIntegracion.enTransaccion;
import static com.stayhub.integracion.EntornoIntegracion.inyectar;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Productores y consumidores JMS de StayHub contra un broker Artemis REAL y un PostgreSQL REAL.
 *
 * <ul>
 *   <li>Los productores son las clases reales de la app (PublicadorXxx), conectadas a Artemis.</li>
 *   <li>Los consumidores son los MDB reales (ConsumidorXxx). Sin servidor de aplicaciones no hay
 *       contenedor de MDB, así que cada mensaje se recibe del broker y se le entrega a mano a
 *       onMessage dentro de una transacción de base de datos.</li>
 *   <li>La reentrega la hace el broker de verdad: se hace rollback de la sesión JMS sin confirmar
 *       (ACK) y Artemis vuelve a entregar el mismo mensaje. Es el duplicado que la idempotencia
 *       tiene que absorber.</li>
 * </ul>
 */
class MensajeriaArtemisIT {

    private static final LocalDate DESDE = LocalDate.now().plusDays(10);
    private static final LocalDate HASTA = DESDE.plusDays(2);

    private static ConnectionFactory fabrica;

    @BeforeAll
    static void iniciarEntorno() {
        fabrica = EntornoIntegracion.fabricaJms();
        EntornoIntegracion.emf(); // levanta también PostgreSQL
    }

    // ================================================================= productores

    @Test
    void publicadorDeSincronizacionEntregaLaSolicitudAlBroker() throws Exception {
        try (JMSContext lector = fabrica.createContext(); JMSContext emisor = fabrica.createContext()) {
            Queue cola = lector.createQueue("CanalesExternosSincronizaciones");
            JMSConsumer consumidor = lector.createConsumer(cola);
            vaciar(consumidor);

            var publicador = new PublicadorSincronizacion();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "cola", cola);
            String solicitudId = publicador.publicarPms(1L, DESDE, HASTA);

            Message recibido = consumidor.receive(10_000);
            assertNotNull(recibido, "El mensaje no llegó a la cola");
            assertEquals(solicitudId, recibido.getJMSCorrelationID());
            SolicitudSincronizacion cuerpo = recibido.getBody(SolicitudSincronizacion.class);
            assertEquals(SolicitudSincronizacion.TipoDestino.PMS, cuerpo.destino());
            assertEquals(1L, cuerpo.hotelId());
            assertEquals(DESDE, cuerpo.desde());
            assertEquals(HASTA, cuerpo.hasta());
        }
    }

    @Test
    void publicadorDePasswordEnviaALaColaConVencimientoDeUnaHora() throws Exception {
        try (JMSContext lector = fabrica.createContext(); JMSContext emisor = fabrica.createContext()) {
            Queue cola = lector.createQueue("UsuariosPassword");
            JMSConsumer consumidor = lector.createConsumer(cola);
            vaciar(consumidor);

            var publicador = new PublicadorEventoPassword();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "cola", cola);
            publicador.publicar(new EventoPassword(1L, "ana@example.com", "Ana", "token-it",
                    TipoEventoPassword.SOLICITUD_RECUPERACION));

            Message recibido = consumidor.receive(10_000);
            assertNotNull(recibido);
            assertEquals("usuario:1:password:SOLICITUD_RECUPERACION", recibido.getJMSCorrelationID());
            long vidaRestante = recibido.getJMSExpiration() - System.currentTimeMillis();
            assertTrue(vidaRestante > 55 * 60_000L && vidaRestante <= 61 * 60_000L,
                    "El mensaje con el token debe vencer en ~1 hora, vence en " + vidaRestante + " ms");
            assertEquals("token-it", recibido.getBody(EventoPassword.class).token());
        }
    }

    @Test
    void elTopicoDeUsuariosEntregaUnaCopiaACadaSuscriptor() throws Exception {
        try (JMSContext suscriptorA = fabrica.createContext();
             JMSContext suscriptorB = fabrica.createContext();
             JMSContext emisor = fabrica.createContext()) {
            Topic topico = suscriptorA.createTopic("UsuariosEventos");
            // En un tópico los suscriptores tienen que existir ANTES de publicar
            JMSConsumer a = suscriptorA.createConsumer(topico);
            JMSConsumer b = suscriptorB.createConsumer(topico);

            var publicador = new PublicadorEventoUsuario();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "topico", topico);
            publicador.publicarUsuarioRegistrado(new EventoUsuarioRegistrado(77L, "ana@example.com", "Ana", "HUESPED"));

            for (JMSConsumer suscriptor : new JMSConsumer[]{a, b}) {
                Message recibido = suscriptor.receive(10_000);
                assertNotNull(recibido, "Cada suscriptor debe recibir su copia");
                assertEquals("usuario:77", recibido.getJMSCorrelationID());
                assertEquals(77L, recibido.getBody(EventoUsuarioRegistrado.class).usuarioId());
            }
        }
    }

    @Test
    void publicadorDePagosEntregaElEventoAlTopico() throws Exception {
        try (JMSContext suscriptor = fabrica.createContext(); JMSContext emisor = fabrica.createContext()) {
            Topic topico = suscriptor.createTopic("PagosEventos");
            JMSConsumer consumidor = suscriptor.createConsumer(topico);

            var publicador = new PublicadorEventoPago();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "topico", topico);
            publicador.publicarPagoAprobado(new EventoPagoAprobado(7L, 9L, new BigDecimal("500.00"), "ARS",
                    "ref-it", LocalDateTime.now()));

            Message recibido = consumidor.receive(10_000);
            assertNotNull(recibido);
            assertEquals("pago:7:reserva:9", recibido.getJMSCorrelationID());
            EventoPagoAprobado evento = recibido.getBody(EventoPagoAprobado.class);
            assertEquals(0, new BigDecimal("500.00").compareTo(evento.monto()));
            assertEquals("ARS", evento.moneda());
        }
    }

    // ================================================================= consumidores + reentrega real

    @Test
    void avisoDeOverbookingReentregadoPorElBrokerEnviaUnSoloCorreo() throws Exception {
        EmailNotificador email = mock(EmailNotificador.class);
        var envio = envioConEmail(email);
        var mdb = new ConsumidorSolicitudNotificacionAsincrona();
        inyectar(mdb, "envio", envio);

        String mensajeId;
        try (JMSContext sesion = fabrica.createContext(JMSContext.SESSION_TRANSACTED);
             JMSContext emisor = fabrica.createContext()) {
            Queue cola = sesion.createQueue("OverbookingNotificaciones");
            JMSConsumer consumidor = sesion.createConsumer(cola);
            vaciar(consumidor);
            sesion.commit();

            var publicador = new PublicadorSolicitudNotificacionAsincrona();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "cola", cola);
            publicador.publicar(new SolicitudNotificacionAsincrona("ana-it@example.com",
                    TipoEvento.RESULTADO_OVERBOOKING, CanalNotificacion.EMAIL, "Cambio en tu reserva", "Te reubicamos"));

            mensajeId = entregarYReentregar(sesion, consumidor, mdb, em -> inyectar(envio, "em", em));
        }

        verify(email, times(1)).enviar(any(SolicitudNotificacionDTO.class));
        assertEquals(1, contar("select count(*) from notificacion_email_enviada where clave = :clave",
                "clave", "overbooking:" + mensajeId));
    }

    @Test
    void recuperacionDePasswordReentregadaPorElBrokerEnviaUnSoloCorreo() throws Exception {
        EmailNotificador email = mock(EmailNotificador.class);
        var envio = envioConEmail(email);
        ConfiguracionMail configuracion = mock(ConfiguracionMail.class);
        when(configuracion.baseUrl()).thenReturn("https://localhost:8443/StayHub");
        var mdb = new ConsumidorEventoPassword();
        inyectar(mdb, "servicio", envio);
        inyectar(mdb, "configuracion", configuracion);

        String mensajeId;
        try (JMSContext sesion = fabrica.createContext(JMSContext.SESSION_TRANSACTED);
             JMSContext emisor = fabrica.createContext()) {
            Queue cola = sesion.createQueue("UsuariosPassword");
            JMSConsumer consumidor = sesion.createConsumer(cola);
            vaciar(consumidor);
            sesion.commit();

            var publicador = new PublicadorEventoPassword();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "cola", cola);
            publicador.publicar(new EventoPassword(2L, "ana-it@example.com", "Ana", "token-it-2",
                    TipoEventoPassword.SOLICITUD_RECUPERACION));

            mensajeId = entregarYReentregar(sesion, consumidor, mdb, em -> inyectar(envio, "em", em));
        }

        verify(email, times(1)).enviar(any(SolicitudNotificacionDTO.class));
        assertEquals(1, contar("select count(*) from notificacion_email_enviada where clave = :clave",
                "clave", "password:" + mensajeId));
    }

    @Test
    void bienvenidaReentregadaPorElBrokerEnviaUnSoloCorreo() throws Exception {
        EmailNotificador email = mock(EmailNotificador.class);
        var envio = envioConEmail(email);
        ConfiguracionMail configuracion = mock(ConfiguracionMail.class);
        when(configuracion.baseUrl()).thenReturn("https://localhost:8443/StayHub");
        var mdb = new ConsumidorEventoUsuarioRegistrado();
        inyectar(mdb, "servicio", envio);
        inyectar(mdb, "configuracion", configuracion);
        long usuarioId = 500_000L + Math.floorMod(System.nanoTime(), 400_000L);

        try (JMSContext sesion = fabrica.createContext(JMSContext.SESSION_TRANSACTED);
             JMSContext emisor = fabrica.createContext()) {
            Topic topico = sesion.createTopic("UsuariosEventos");
            JMSConsumer suscriptor = sesion.createConsumer(topico);

            var publicador = new PublicadorEventoUsuario();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "topico", topico);
            publicador.publicarUsuarioRegistrado(
                    new EventoUsuarioRegistrado(usuarioId, "ana-it@example.com", "Ana", "HUESPED"));

            entregarYReentregar(sesion, suscriptor, mdb, em -> inyectar(envio, "em", em));
        }

        verify(email, times(1)).enviar(any(SolicitudNotificacionDTO.class));
        assertEquals(1, contar("select count(*) from notificacion_email_enviada where clave = :clave",
                "clave", "bienvenida:" + usuarioId));
    }

    @Test
    void pagoReentregadoPorElBrokerEnviaComprobanteYConfirmacionUnaSolaVez() throws Exception {
        EmailNotificador email = mock(EmailNotificador.class);
        var envio = envioConEmail(email);
        var mdb = new ConsumidorEventoPago();
        inyectar(mdb, "envio", envio);
        long pagoId = 700_000L + Math.floorMod(System.nanoTime(), 200_000L);

        try (JMSContext sesion = fabrica.createContext(JMSContext.SESSION_TRANSACTED);
             JMSContext emisor = fabrica.createContext()) {
            Topic topico = sesion.createTopic("PagosEventos");
            JMSConsumer suscriptor = sesion.createConsumer(topico);

            var publicador = new PublicadorEventoPago();
            inyectar(publicador, "contexto", emisor);
            inyectar(publicador, "topico", topico);
            var reserva = new EventoPagoAprobado.ReservaPagada("ana-it@example.com", 2L, "DOBLE",
                    LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3));
            publicador.publicarPagoAprobado(new EventoPagoAprobado(pagoId, 9L, new BigDecimal("500.00"), "ARS",
                    "ref-it", LocalDateTime.now(), reserva));

            entregarYReentregar(sesion, suscriptor, mdb, em -> inyectar(envio, "em", em));
        }

        // Dos correos distintos (comprobante + confirmación), cada uno una sola vez
        verify(email, times(2)).enviar(any(SolicitudNotificacionDTO.class));
        assertEquals(1, contar("select count(*) from notificacion_email_enviada where clave = :clave",
                "clave", "pago:" + pagoId + ":ana-it@example.com"));
        assertEquals(1, contar("select count(*) from notificacion_email_enviada where clave = :clave",
                "clave", "reserva-pagada:" + pagoId + ":9"));
    }

    @Test
    void sincronizacionReentregadaPorElBrokerSeEjecutaUnaSolaVez() throws Exception {
        ProcesadorSincronizacion procesador = mock(ProcesadorSincronizacion.class);
        var mdb = consumidorDeSincronizacion(procesador);
        var idempotente = new SincronizacionIdempotente();
        inyectar(mdb, "idempotente", idempotente);

        String solicitudId;
        try (JMSContext sesion = fabrica.createContext(JMSContext.SESSION_TRANSACTED);
             JMSContext emisor = fabrica.createContext()) {
            Queue cola = sesion.createQueue("CanalesExternosSincronizaciones");
            JMSConsumer consumidor = sesion.createConsumer(cola);
            vaciar(consumidor);
            sesion.commit();

            solicitudId = publicarSincronizacionPms(emisor, cola);
            entregarYReentregar(sesion, consumidor, mdb, em -> inyectar(idempotente, "em", em));
        }

        verify(procesador, times(1)).sincronizarPms(1L, DESDE, HASTA);
        assertEquals(1, contar("select count(*) from sincronizacion_procesada where solicitud_id = :id",
                "id", solicitudId));
    }

    @Test
    void siLaSincronizacionFallaElBrokerReintentaYLaClaveSoloSeGuardaAlTenerExito() throws Exception {
        ProcesadorSincronizacion procesador = mock(ProcesadorSincronizacion.class);
        // Primer intento: el PMS no responde. Segundo intento: responde bien.
        doThrow(new IllegalStateException("timeout del PMS")).doNothing()
                .when(procesador).sincronizarPms(1L, DESDE, HASTA);
        var mdb = consumidorDeSincronizacion(procesador);
        var idempotente = new SincronizacionIdempotente();
        inyectar(mdb, "idempotente", idempotente);
        Consumer<EntityManager> conectar = em -> inyectar(idempotente, "em", em);

        String solicitudId;
        try (JMSContext sesion = fabrica.createContext(JMSContext.SESSION_TRANSACTED);
             JMSContext emisor = fabrica.createContext()) {
            Queue cola = sesion.createQueue("CanalesExternosSincronizaciones");
            JMSConsumer consumidor = sesion.createConsumer(cola);
            vaciar(consumidor);
            sesion.commit();

            solicitudId = publicarSincronizacionPms(emisor, cola);

            final Message primera = consumidor.receive(10_000);
            assertNotNull(primera);
            assertThrows(IllegalStateException.class, () -> entregar(mdb, primera, conectar));
            assertEquals(0, contar("select count(*) from sincronizacion_procesada where solicitud_id = :id",
                    "id", solicitudId), "Un intento fallido no debe dejar la clave guardada");
            sesion.rollback(); // sin ACK: el broker reentrega

            Message segunda = consumidor.receive(10_000);
            assertNotNull(segunda, "El broker debería reentregar tras el fallo");
            assertTrue(segunda.getJMSRedelivered());
            entregar(mdb, segunda, conectar);
            sesion.commit();
        }

        verify(procesador, times(2)).sincronizarPms(1L, DESDE, HASTA);
        assertEquals(1, contar("select count(*) from sincronizacion_procesada where solicitud_id = :id",
                "id", solicitudId));
    }

    // ================================================================= utilidades

    private static EnvioCorreoIdempotente envioConEmail(EmailNotificador email) {
        var envio = new EnvioCorreoIdempotente();
        inyectar(envio, "email", email);
        return envio;
    }

    private static ConsumidorSincronizacion consumidorDeSincronizacion(ProcesadorSincronizacion procesador) {
        var mdb = new ConsumidorSincronizacion();
        inyectar(mdb, "procesador", procesador);
        return mdb;
    }

    private static String publicarSincronizacionPms(JMSContext emisor, Queue cola) {
        var publicador = new PublicadorSincronizacion();
        inyectar(publicador, "contexto", emisor);
        inyectar(publicador, "cola", cola);
        return publicador.publicarPms(1L, DESDE, HASTA);
    }

    /** Descarta lo que haya quedado en la cola de pruebas anteriores. */
    private static void vaciar(JMSConsumer consumidor) {
        while (consumidor.receive(300) != null) {
            // solo consumir
        }
    }

    /** Entrega el mensaje al MDB dentro de una transacción de base de datos (commit si sale bien). */
    private static void entregar(MessageListener mdb, Message mensaje, Consumer<EntityManager> conectar) {
        enTransaccion(em -> {
            conectar.accept(em);
            mdb.onMessage(mensaje);
            return null;
        });
    }

    /**
     * Recibe el mensaje, lo procesa, NO lo confirma (rollback de la sesión JMS) y deja que el
     * broker lo reentregue; el MDB lo procesa por segunda vez y recién ahí se confirma.
     * Devuelve el JMSMessageID, que es el mismo en la reentrega.
     */
    private static String entregarYReentregar(JMSContext sesion, JMSConsumer consumidor, MessageListener mdb,
                                              Consumer<EntityManager> conectar) throws Exception {
        Message primera = consumidor.receive(10_000);
        assertNotNull(primera, "El mensaje no llegó al consumidor");
        String id = primera.getJMSMessageID();
        entregar(mdb, primera, conectar);

        sesion.rollback(); // sin ACK: el broker tiene que volver a entregarlo

        Message segunda = consumidor.receive(10_000);
        assertNotNull(segunda, "El broker debería reentregar el mensaje");
        assertEquals(id, segunda.getJMSMessageID(), "Es el mismo mensaje, reentregado");
        assertTrue(segunda.getJMSRedelivered(), "Tiene que figurar como reentregado");
        entregar(mdb, segunda, conectar);

        sesion.commit();
        return id;
    }
}