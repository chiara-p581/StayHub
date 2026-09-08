package com.stayhub.pagos.messaging;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventoPagoAprobado(
        Long pagoId,
        Long reservaId,
        BigDecimal monto,
        String moneda,
        String referenciaPasarela,
        LocalDateTime fechaAprobacion) implements Serializable { }