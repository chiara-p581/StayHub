package com.stayhub.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoResponse(
        Long id,
        Long reservaId,
        BigDecimal monto,
        String moneda,
        String estado,
        String referenciaPasarela,
        LocalDateTime fechaPago) { }