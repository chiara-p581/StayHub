package com.stayhub.notificaciones.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.model.EnvioCorreo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** Orden de operaciones de EnvioCorreoIdempotente: enviar, registrar y vaciar el contexto. */
class EnvioCorreoIdempotenteMutantesTest {

    private void inyectar(Object objeto, String nombre, Object valor) throws Exception {
        Field campo = objeto.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(objeto, valor);
    }

    private SolicitudNotificacionDTO solicitud() {
        return new SolicitudNotificacionDTO("test@example.com", TipoEvento.RESULTADO_OVERBOOKING,
                CanalNotificacion.EMAIL, "Hola", "Mensaje");
    }

    @Test void envioNuevoEnviaRegistraLaClaveYHaceFlushEnEseOrden() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EmailNotificador email = mock(EmailNotificador.class);
        Query consulta = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(consulta);
        when(consulta.setParameter(eq("clave"), any())).thenReturn(consulta);
        EnvioCorreoIdempotente servicio = new EnvioCorreoIdempotente();
        inyectar(servicio, "em", em);
        inyectar(servicio, "email", email);
        SolicitudNotificacionDTO solicitud = solicitud();

        servicio.enviar("pago:42", solicitud);

        InOrder orden = inOrder(em, email);
        orden.verify(email).enviar(solicitud);
        ArgumentCaptor<EnvioCorreo> captor = ArgumentCaptor.forClass(EnvioCorreo.class);
        orden.verify(em).persist(captor.capture());
        orden.verify(em).flush();
        Field clave = EnvioCorreo.class.getDeclaredField("clave");
        clave.setAccessible(true);
        assertEquals("pago:42", clave.get(captor.getValue()));
    }

    @Test void claveYaRegistradaNoEnviaNiPersisteNiHaceFlush() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EmailNotificador email = mock(EmailNotificador.class);
        Query consulta = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(consulta);
        when(consulta.setParameter(eq("clave"), any())).thenReturn(consulta);
        when(em.find(EnvioCorreo.class, "pago:42")).thenReturn(new EnvioCorreo("pago:42"));
        EnvioCorreoIdempotente servicio = new EnvioCorreoIdempotente();
        inyectar(servicio, "em", em);
        inyectar(servicio, "email", email);

        servicio.enviar("pago:42", solicitud());

        verifyNoInteractions(email);
        verify(em, never()).persist(any());
        verify(em, never()).flush();
    }
}
