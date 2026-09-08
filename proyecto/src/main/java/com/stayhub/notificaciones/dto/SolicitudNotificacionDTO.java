package com.stayhub.notificaciones.dto;

public record SolicitudNotificacionDTO(
        String destinatario,
        TipoEvento tipoEvento,
        CanalNotificacion canalPreferido,
        String asunto,
        String mensaje) { }