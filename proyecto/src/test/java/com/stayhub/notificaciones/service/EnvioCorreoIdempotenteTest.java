package com.stayhub.notificaciones.service;

import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.model.EnvioCorreo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EnvioCorreoIdempotenteTest {

    private void inyectar(
            Object objeto,
            String nombre,
            Object valor
    ) throws Exception {
        Field campo = objeto.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(objeto, valor);
    }

    @Test
    void noReenviaUnCorreoRegistrado() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EmailNotificador email = mock(EmailNotificador.class);
        Query consulta = mock(Query.class);

        when(em.createNativeQuery(anyString())).thenReturn(consulta);

        when(consulta.setParameter(eq("clave"), any()))
                .thenReturn(consulta);

        when(em.find(EnvioCorreo.class, "pago:1"))
                .thenReturn(new EnvioCorreo("pago:1"));

        EnvioCorreoIdempotente servicio = new EnvioCorreoIdempotente();

        inyectar(servicio, "em", em);
        inyectar(servicio, "email", email);

        servicio.enviar("pago:1", null);

        verifyNoInteractions(email);
        verify(em, never()).persist(any());
    }

    @Test
    void envioFallidoNoSeMarcaComoExitoso() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EmailNotificador email = mock(EmailNotificador.class);
        Query consulta = mock(Query.class);

        when(em.createNativeQuery(anyString())).thenReturn(consulta);

        when(consulta.setParameter(eq("clave"), any()))
                .thenReturn(consulta);

        doThrow(new IllegalStateException("SMTP caído"))
                .when(email)
                .enviar(any());

        EnvioCorreoIdempotente servicio = new EnvioCorreoIdempotente();

        inyectar(servicio, "em", em);
        inyectar(servicio, "email", email);

        SolicitudNotificacionDTO solicitud = new SolicitudNotificacionDTO(
                "test@example.com",
                TipoEvento.RESULTADO_OVERBOOKING,
                CanalNotificacion.EMAIL,
                "Hola",
                "Hola"
        );

        assertThrows(
                IllegalStateException.class,
                () -> servicio.enviar("overbooking:1", solicitud)
        );

        verify(em, never()).persist(any());
    }
}