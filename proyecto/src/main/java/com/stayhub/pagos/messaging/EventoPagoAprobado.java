package com.stayhub.pagos.messaging;

import com.stayhub.pagos.model.Pago;
import com.stayhub.reservas.dto.ReservaResponse;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EventoPagoAprobado(
        Long pagoId,
        Long reservaId,
        BigDecimal monto,
        String moneda,
        String referenciaPasarela,
        LocalDateTime fechaAprobacion,
        ReservaPagada reservaPagada) implements Serializable {

    /** Constructor anterior, sin snapshot de la reserva. */
    public EventoPagoAprobado(Long pagoId, Long reservaId, BigDecimal monto, String moneda,
                              String referenciaPasarela, LocalDateTime fechaAprobacion) {
        this(pagoId, reservaId, monto, moneda, referenciaPasarela, fechaAprobacion, null);
    }

    /** Arma el evento con una "foto" de la reserva tomada en el momento del pago. */
    public static EventoPagoAprobado desde(Pago pago, ReservaResponse reserva) {
        return new EventoPagoAprobado(pago.getId(), reserva.id(), pago.getMonto(), pago.getMoneda(),
                pago.getReferenciaPasarela(), pago.getFechaPago(),
                new ReservaPagada(reserva.huespedEmail(), reserva.hotelId(), reserva.tipoHabitacion(),
                        reserva.checkIn(), reserva.checkOut()));
    }

    public record ReservaPagada(String email, Long hotelId, String tipoHabitacion,
                                LocalDate checkIn, LocalDate checkOut) implements Serializable { }
}