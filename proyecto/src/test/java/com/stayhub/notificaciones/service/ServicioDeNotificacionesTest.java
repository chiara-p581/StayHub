package com.stayhub.notificaciones.service;

import com.stayhub.notificaciones.client.NotificadorCanal;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.exception.CodigoErrorNotificacion;
import com.stayhub.notificaciones.exception.NotificacionException;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** El servicio elige la estrategia por canal sin switch (OCP) y falla seguro si falta una. */
class ServicioDeNotificacionesTest {
    private ServicioDeNotificacionesImpl servicio;
    private NotificadorCanal sms;
    private NotificadorCanal push;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        sms = mock(NotificadorCanal.class);
        push = mock(NotificadorCanal.class);
        when(sms.canal()).thenReturn(CanalNotificacion.SMS);
        when(push.canal()).thenReturn(CanalNotificacion.PUSH);
        Instance<NotificadorCanal> instancias = mock(Instance.class);
        when(instancias.iterator()).thenAnswer(i -> List.of(sms, push).iterator());
        servicio = new ServicioDeNotificacionesImpl();
        servicio.notificadores = instancias;
    }

    @Test
    void usaElNotificadorDelCanalPedido() {
        var solicitud = new SolicitudNotificacionDTO("+5491100000001", TipoEvento.RECORDATORIO,
                CanalNotificacion.PUSH, "Hola", "Prueba");
        assertTrue(servicio.enviar(solicitud).enviado());
        verify(push).enviar(solicitud);
        verify(sms, never()).enviar(any());
    }

    @Test
    void canalSinNotificadorSeRechazaSinEnviar() {
        var solicitud = new SolicitudNotificacionDTO("persona@test.com", TipoEvento.RECORDATORIO,
                CanalNotificacion.EMAIL, "Hola", "Prueba");
        var error = assertThrows(NotificacionException.class, () -> servicio.enviar(solicitud));
        assertEquals(CodigoErrorNotificacion.CANAL_NO_SOPORTADO, error.getCodigo());
        verify(sms, never()).enviar(any());
        verify(push, never()).enviar(any());
    }
}