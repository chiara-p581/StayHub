package com.stayhub.pagos.dto;

import java.math.BigDecimal;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

/** Un solo cobro para todas las reservas confirmadas durante el checkout. */
public record PagoLoteRequest(List<Long> reservaIds, BigDecimal monto, String moneda, String claveIdempotencia) {
    // JSON-B necesita un creador inequívoco porque conservamos el constructor
    // de compatibilidad sin clave de idempotencia.
    @JsonbCreator
    public PagoLoteRequest(@JsonbProperty("reservaIds") List<Long> reservaIds,
            @JsonbProperty("monto") BigDecimal monto,
            @JsonbProperty("moneda") String moneda,
            @JsonbProperty("claveIdempotencia") String claveIdempotencia) {
        this.reservaIds = reservaIds;
        this.monto = monto;
        this.moneda = moneda;
        this.claveIdempotencia = claveIdempotencia;
    }

    public PagoLoteRequest(List<Long> reservaIds, BigDecimal monto, String moneda) {
        this(reservaIds, monto, moneda, null);
    }
}
