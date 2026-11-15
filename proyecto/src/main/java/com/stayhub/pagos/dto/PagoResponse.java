package com.stayhub.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PagoResponse(
        Long id,
        Long usuarioId,
        Long reservaId,
        List<Long> reservaIds,
        BigDecimal monto,
        String moneda,
        String estado,
        String referenciaPasarela,
        LocalDateTime fechaPago,
        LocalDateTime fechaReembolso,
        String motivoReembolso) { }
