package com.stayhub.canalesexternos.dto;

import java.time.OffsetDateTime;

public record ResultadoSincronizacionDTO(
        String solicitudId,
        boolean exitoso,
        String destino,
        String mensaje,
        int elementosProcesados,
        OffsetDateTime fecha) {

    public static ResultadoSincronizacionDTO exitoso(String destino, String mensaje, int cantidad) {
        return new ResultadoSincronizacionDTO(null, true, destino, mensaje, cantidad, OffsetDateTime.now());
    }

    public static ResultadoSincronizacionDTO encolado(String solicitudId, String destino) {
        return new ResultadoSincronizacionDTO(solicitudId, true, destino,
                "Solicitud de sincronización aceptada para procesamiento asincrónico", 0, OffsetDateTime.now());
    }
}
