package com.stayhub.pagos.dto;

import java.math.BigDecimal;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

public record PagoRequest(
        Long reservaId,
        BigDecimal monto,
        String moneda,
        String claveIdempotencia) {
    // JSON-B necesita un creador inequívoco porque conservamos el constructor
    // de compatibilidad sin clave de idempotencia.
    @JsonbCreator
    public PagoRequest(@JsonbProperty("reservaId") Long reservaId,
            @JsonbProperty("monto") BigDecimal monto,
            @JsonbProperty("moneda") String moneda,
            @JsonbProperty("claveIdempotencia") String claveIdempotencia) {
        this.reservaId = reservaId;
        this.monto = monto;
        this.moneda = moneda;
        this.claveIdempotencia = claveIdempotencia;
    }

    public PagoRequest(Long reservaId, BigDecimal monto, String moneda) {
        this(reservaId, monto, moneda, null);
    }
}
