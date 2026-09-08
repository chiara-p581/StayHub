package com.stayhub.notificaciones.dto;

import java.time.OffsetDateTime;

public record ResultadoEnvioNotificacionDTO(
        boolean enviado,
        CanalNotificacion canalUtilizado,
        OffsetDateTime fecha,
        String mensajeError) {

    public static ResultadoEnvioNotificacionDTO exitoso(CanalNotificacion canal) {
        return new ResultadoEnvioNotificacionDTO(true, canal, OffsetDateTime.now(), null);
    }
}