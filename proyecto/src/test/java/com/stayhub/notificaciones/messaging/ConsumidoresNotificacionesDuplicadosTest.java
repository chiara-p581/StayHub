package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.model.EnvioCorreo;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.servicioDeOverbooking.messaging.SolicitudNotificacionAsincrona;
import com.stayhub.usuarios.messaging.EventoPassword;
import com.stayhub.usuarios.messaging.TipoEventoPassword;
import jakarta.jms.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Un mismo mensaje entregado dos veces produce un único correo. */
class ConsumidoresNotificacionesDuplicadosTest {

    private EmailNotificador email;
    private EnvioCorreoIdempotente envio;
    private final AtomicBoolean registrado = new AtomicBoolean(false); // simula notificacion_email_enviada

    private void inyectar(Object o, String nombre, Object valor) throws Exception {
        var f = o.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        f.set(o, valor);
    }

    @BeforeEach
    void preparar() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query consulta = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(consulta);
        when(consulta.setParameter(eq("clave"), any())).thenReturn(consulta);
        when(em.find(eq(EnvioCorreo.class), anyString()))
                .thenAnswer(i -> registrado.get() ? mock(EnvioCorreo.class) : null);
        doAnswer(i -> {
            registrado.set(true);
            return null;
        }).when(em).persist(any(EnvioCorreo.class));

        email = mock(EmailNotificador.class);
        envio = new EnvioCorreoIdempotente();
        inyectar(envio, "em", em);
        inyectar(envio, "email", email);
    }

    @Test
    void avisoDeOverbookingDuplicadoEnviaUnSoloCorreo() throws Exception {
        var consumidor = new ConsumidorSolicitudNotificacionAsincrona();
        inyectar(consumidor, "envio", envio);

        Message m = mock(Message.class);
        when(m.getJMSMessageID()).thenReturn("ID:overbooking-1");
        when(m.getBody(SolicitudNotificacionAsincrona.class)).thenReturn(new SolicitudNotificacionAsincrona(
                "ana@example.com", TipoEvento.RESULTADO_OVERBOOKING, CanalNotificacion.EMAIL,
                "Cambio en tu reserva", "Te reubicamos"));

        consumidor.onMessage(m);
        consumidor.onMessage(m);

        verify(email, times(1)).enviar(any(SolicitudNotificacionDTO.class));
    }

    @Test
    void passwordDuplicadoEnviaUnSoloCorreo() throws Exception {
        var consumidor = new ConsumidorEventoPassword();
        ConfiguracionMail cfg = mock(ConfiguracionMail.class);
        when(cfg.baseUrl()).thenReturn("https://localhost:8443");
        inyectar(consumidor, "servicio", envio);
        inyectar(consumidor, "configuracion", cfg);

        Message m = mock(Message.class);
        when(m.getJMSMessageID()).thenReturn("ID:password-1");
        when(m.getBody(EventoPassword.class)).thenReturn(
                new EventoPassword(1L, "ana@example.com", "Ana", "token-123", TipoEventoPassword.SOLICITUD_RECUPERACION));

        consumidor.onMessage(m);
        consumidor.onMessage(m);

        verify(email, times(1)).enviar(any(SolicitudNotificacionDTO.class));
    }
}