package com.stayhub.pagos.dto;

import java.math.BigDecimal;
import java.util.List;

/** Un solo cobro para todas las reservas confirmadas durante el checkout. */
public record PagoLoteRequest(List<Long> reservaIds, BigDecimal monto, String moneda, String claveIdempotencia) {
    public PagoLoteRequest(List<Long> reservaIds, BigDecimal monto, String moneda) {
        this(reservaIds, monto, moneda, null);
    }
}
