package com.stayhub.pagos.dto;

import java.math.BigDecimal;

public record PagoRequest(
        Long reservaId,
        BigDecimal monto,
        String moneda) { }