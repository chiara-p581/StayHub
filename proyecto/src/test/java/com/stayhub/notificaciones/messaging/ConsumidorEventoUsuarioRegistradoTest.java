package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.usuarios.messaging.EventoUsuarioRegistrado;
import jakarta.jms.Message;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static com.stayhub.seguridad.IdentidadFixture.campo;

class ConsumidorEventoUsuarioRegistradoTest {
    @Test void eventoDeRegistroGeneraUnaUnicaBienvenida() throws Exception {
        var consumidor=new ConsumidorEventoUsuarioRegistrado();var servicio=mock(EnvioCorreoIdempotente.class);var config=mock(ConfiguracionMail.class);var mensaje=mock(Message.class);
        campo(consumidor,"servicio",servicio);campo(consumidor,"configuracion",config);when(config.baseUrl()).thenReturn("https://localhost:8443/StayHub");when(mensaje.getBody(EventoUsuarioRegistrado.class)).thenReturn(new EventoUsuarioRegistrado(77L,"qa-bienvenida@test.com","QA","HUESPED"));
        consumidor.onMessage(mensaje);verify(servicio,times(1)).enviar(eq("bienvenida:77"),argThat((SolicitudNotificacionDTO s)->s.destinatario().equals("qa-bienvenida@test.com")&&s.asunto().contains("Bienvenido")));
    }
}
