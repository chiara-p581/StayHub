package com.stayhub.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PagoAdminResponse(
        Long id,
        Long usuarioId,
        String usuarioNombre,
        String usuarioEmail,
        List<ReservaPagoResponse> reservas,
        BigDecimal monto,
        String moneda,
        String estado,
        String referenciaPasarela,
        LocalDateTime fechaPago,
        LocalDateTime fechaReembolso,
        String motivoReembolso) {

    public record ReservaPagoResponse(Long id, Long hotelId, String hotelNombre, String estado) { }
}
