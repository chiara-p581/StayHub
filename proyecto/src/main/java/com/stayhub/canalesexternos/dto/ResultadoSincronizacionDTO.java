package com.stayhub.canalesexternos.dto;

import java.time.OffsetDateTime;

public record ResultadoSincronizacionDTO(
        boolean exitoso,
        String destino,
        String mensaje,
        int elementosProcesados,
        OffsetDateTime fecha) {

    public static ResultadoSincronizacionDTO exitoso(String destino, String mensaje, int cantidad) {
        return new ResultadoSincronizacionDTO(true, destino, mensaje, cantidad, OffsetDateTime.now());
    }
}
