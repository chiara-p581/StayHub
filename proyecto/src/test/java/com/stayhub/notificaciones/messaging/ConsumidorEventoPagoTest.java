package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import com.stayhub.reservas.service.ServicioDeReservas;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsumidorEventoPagoTest {

    private void inyectar(Object o, String nombre, Object valor) throws Exception {
        var f = o.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        f.set(o, valor);
    }

    @Test
    void generaComprobanteSeparadoYConfirmacionConSnapshot() throws Exception {
        var envio = mock(EnvioCorreoIdempotente.class);
        var reservas = mock(ServicioDeReservas.class);
        var c = new ConsumidorEventoPago();
        inyectar(c, "envio", envio);
        inyectar(c, "reservas", reservas);

        var snapshot = new EventoPagoAprobado.ReservaPagada("test@example.com", 2L, "DOBLE",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3));
        var e = new EventoPagoAprobado(7L, 9L, new BigDecimal("500"), "ARS", "ref", LocalDateTime.now(), snapshot);
        var m = mock(Message.class);
        when(m.getBody(EventoPagoAprobado.class)).thenReturn(e);

        c.onMessage(m);

        var dto = ArgumentCaptor.forClass(SolicitudNotificacionDTO.class);
        verify(envio).enviar(eq("pago:7:test@example.com"), dto.capture());
        assertEquals(TipoEvento.AVISO_PAGO, dto.getValue().tipoEvento());
        assertTrue(dto.getValue().mensaje().contains("500"));

        verify(envio).enviar(eq("reserva-pagada:7:9"), dto.capture());
        assertEquals(TipoEvento.CONFIRMACION_RESERVA, dto.getValue().tipoEvento());
        assertFalse(dto.getValue().mensaje().contains("500"));

        verifyNoInteractions(reservas);
    }

    @Test
    void faltaDeDestinatarioFallaParaReintentar() throws Exception {
        var c = new ConsumidorEventoPago();
        var envio = mock(EnvioCorreoIdempotente.class);
        inyectar(c, "envio", envio);

        var m = mock(Message.class);
        when(m.getBody(EventoPagoAprobado.class)).thenReturn(new EventoPagoAprobado(1L, 2L, BigDecimal.ONE,
                "ARS", "ref", LocalDateTime.now(),
                new EventoPagoAprobado.ReservaPagada(null, 3L, "DOBLE", LocalDate.now(), LocalDate.now().plusDays(1))));

        assertThrows(IllegalStateException.class, () -> c.onMessage(m));
        verifyNoInteractions(envio);
    }
}