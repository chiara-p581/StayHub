package com.stayhub.pagos.dto;

import java.math.BigDecimal;

public record PagoRequest(
        Long reservaId,
        BigDecimal monto,
        String moneda,
        String claveIdempotencia) {
    public PagoRequest(Long reservaId, BigDecimal monto, String moneda) {
        this(reservaId, monto, moneda, null);
    }
}
