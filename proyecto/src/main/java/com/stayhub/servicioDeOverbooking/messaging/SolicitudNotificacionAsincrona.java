package com.stayhub.servicioDeOverbooking.messaging;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.TipoEvento;

import java.io.Serializable;

public record SolicitudNotificacionAsincrona(
        String destinatario,
        TipoEvento tipoEvento,
        CanalNotificacion canalPreferido,
        String asunto,
        String mensaje
) implements Serializable {
}