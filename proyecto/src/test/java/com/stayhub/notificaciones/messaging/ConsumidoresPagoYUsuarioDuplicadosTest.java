package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.model.EnvioCorreo;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import com.stayhub.usuarios.messaging.EventoUsuarioRegistrado;
import jakarta.jms.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** El mismo evento entregado dos veces no repite ningún correo. */
class ConsumidoresPagoYUsuarioDuplicadosTest {

    private EmailNotificador email;
    private EnvioCorreoIdempotente envio;
    private final Set<String> tabla = new HashSet<>(); // simula notificacion_email_enviada

    private void inyectar(Object o, String nombre, Object valor) throws Exception {
        var f = o.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        f.set(o, valor);
    }

    private String claveDe(EnvioCorreo envioCorreo) throws Exception {
        var f = EnvioCorreo.class.getDeclaredField("clave");
        f.setAccessible(true);
        return (String) f.get(envioCorreo);
    }

    @BeforeEach
    void preparar() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query consulta = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(consulta);
        when(consulta.setParameter(eq("clave"), any())).thenReturn(consulta);
        when(em.find(eq(EnvioCorreo.class), anyString())).thenAnswer(i ->
                tabla.contains(i.<String>getArgument(1)) ? mock(EnvioCorreo.class) : null);
        doAnswer(i -> {
            tabla.add(claveDe(i.getArgument(0)));
            return null;
        }).when(em).persist(any(EnvioCorreo.class));

        email = mock(EmailNotificador.class);
        envio = new EnvioCorreoIdempotente();
        inyectar(envio, "em", em);
        inyectar(envio, "email", email);
    }

    @Test
    void pagoDuplicadoEnviaComprobanteYConfirmacionUnaSolaVez() throws Exception {
        var consumidor = new ConsumidorEventoPago();
        inyectar(consumidor, "envio", envio);

        var snapshot = new EventoPagoAprobado.ReservaPagada("test@example.com", 2L, "DOBLE",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3));
        var evento = new EventoPagoAprobado(7L, 9L, new BigDecimal("500"), "ARS", "ref",
                LocalDateTime.now(), snapshot);
        Message m = mock(Message.class);
        when(m.getBody(EventoPagoAprobado.class)).thenReturn(evento);

        consumidor.onMessage(m);
        consumidor.onMessage(m); // reentrega del broker

        // 2 correos distintos (comprobante + confirmación), cada uno una sola vez
        verify(email, times(2)).enviar(any(SolicitudNotificacionDTO.class));
    }

    @Test
    void bienvenidaDuplicadaEnviaUnSoloCorreo() throws Exception {
        var consumidor = new ConsumidorEventoUsuarioRegistrado();
        ConfiguracionMail cfg = mock(ConfiguracionMail.class);
        when(cfg.baseUrl()).thenReturn("https://localhost:8443/StayHub");
        inyectar(consumidor, "servicio", envio);
        inyectar(consumidor, "configuracion", cfg);

        Message m = mock(Message.class);
        when(m.getBody(EventoUsuarioRegistrado.class))
                .thenReturn(new EventoUsuarioRegistrado(77L, "ana@example.com", "Ana", "HUESPED"));

        consumidor.onMessage(m);
        consumidor.onMessage(m);

        verify(email, times(1)).enviar(any(SolicitudNotificacionDTO.class));
    }
}