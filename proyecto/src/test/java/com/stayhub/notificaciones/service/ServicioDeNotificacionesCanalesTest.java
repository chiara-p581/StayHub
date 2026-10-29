package com.stayhub.notificaciones.service;

import com.stayhub.notificaciones.client.*;
import com.stayhub.notificaciones.dto.*;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioDeNotificacionesCanalesTest {
    ServicioDeNotificacionesImpl servicio; EmailNotificador email; SmsNotificador sms; PushNotificador push;
    @BeforeEach @SuppressWarnings("unchecked") void preparar(){
        servicio=new ServicioDeNotificacionesImpl();email=mock(EmailNotificador.class);sms=mock(SmsNotificador.class);push=mock(PushNotificador.class);
        when(email.canal()).thenReturn(CanalNotificacion.EMAIL);when(sms.canal()).thenReturn(CanalNotificacion.SMS);when(push.canal()).thenReturn(CanalNotificacion.PUSH);
        Instance<NotificadorCanal> notificadores=mock(Instance.class);when(notificadores.iterator()).thenAnswer(i->List.<NotificadorCanal>of(email,sms,push).iterator());servicio.notificadores=notificadores;
    }
    SolicitudNotificacionDTO solicitud(CanalNotificacion canal,String destino){return new SolicitudNotificacionDTO(destino,TipoEvento.RECORDATORIO,canal,"QA","mensaje");}
    @Test void seleccionaUnicamenteElAdaptadorDelCanalPedido(){
        servicio.enviar(solicitud(CanalNotificacion.EMAIL,"qa-email@test.com"));servicio.enviar(solicitud(CanalNotificacion.SMS,"qa-sms"));servicio.enviar(solicitud(CanalNotificacion.PUSH,"qa-push"));
        verify(email).enviar(any());verify(sms).enviar(any());verify(push).enviar(any());
    }
    @Test void proveedorCaidoSeTraduceYNoSeInformaExito(){
        doThrow(new IllegalStateException("smtp fuera de servicio")).when(email).enviar(any());
        assertThrows(RuntimeException.class,()->servicio.enviar(solicitud(CanalNotificacion.EMAIL,"qa-falla@test.com")));
        verifyNoInteractions(sms,push);
    }
}
