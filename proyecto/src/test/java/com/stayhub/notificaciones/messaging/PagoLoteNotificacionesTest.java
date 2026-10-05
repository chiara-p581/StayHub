package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.model.EnvioCorreo;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import jakarta.jms.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagoLoteNotificacionesTest {

    private void inyectar(Object o, String n, Object v) throws Exception {
        var f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        f.set(o, v);
    }

    @Test
    void loteYRedeliveryEnviaUnComprobanteYDosReservas() throws Exception {
        var em = mock(EntityManager.class);
        var smtp = mock(EmailNotificador.class);
        var q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(eq("clave"), any())).thenReturn(q);

        // Simula la tabla de mails enviados
        Map<String, EnvioCorreo> enviados = new HashMap<>();
        when(em.find(eq(EnvioCorreo.class), any())).thenAnswer(i -> enviados.get(i.getArgument(1)));
        doAnswer(i -> {
            EnvioCorreo registro = i.getArgument(0);
            var f = EnvioCorreo.class.getDeclaredField("clave");
            f.setAccessible(true);
            enviados.put((String) f.get(registro), registro);
            return null;
        }).when(em).persist(any());

        var envio = new EnvioCorreoIdempotente();
        inyectar(envio, "em", em);
        inyectar(envio, "email", smtp);
        var consumidor = new ConsumidorEventoPago();
        inyectar(consumidor, "envio", envio);

        var snapshot = new EventoPagoAprobado.ReservaPagada("test@example.com", 2L, "DOBLE",
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12));

        // Lote con reservas 10 y 11, y cada evento llega dos veces (reentrega de JMS)
        for (long id : new long[]{10L, 11L, 10L, 11L}) {
            var m = mock(Message.class);
            when(m.getBody(EventoPagoAprobado.class)).thenReturn(
                    new EventoPagoAprobado(7L, id, new BigDecimal("1000"), "ARS", "ref", LocalDateTime.now(), snapshot));
            consumidor.onMessage(m);
        }

        // Resultado esperado: 1 comprobante + 2 confirmaciones, sin duplicados
        var captor = ArgumentCaptor.forClass(SolicitudNotificacionDTO.class);
        verify(smtp, times(3)).enviar(captor.capture());
        assertEquals(1, captor.getAllValues().stream().filter(d -> d.tipoEvento() == TipoEvento.AVISO_PAGO).count());
        assertEquals(2, captor.getAllValues().stream().filter(d -> d.tipoEvento() == TipoEvento.CONFIRMACION_RESERVA).count());
    }
}