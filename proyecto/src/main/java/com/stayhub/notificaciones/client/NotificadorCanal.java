package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;

public interface NotificadorCanal {
    void enviar(SolicitudNotificacionDTO solicitud);
}