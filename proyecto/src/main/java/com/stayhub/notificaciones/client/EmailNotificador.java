package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailNotificador implements NotificadorCanal {
    public void enviar(SolicitudNotificacionDTO solicitud) {
        // TODO: integrar con un proveedor real de email (SMTP, API externa).
        System.out.println("[EMAIL] Para: " + solicitud.destinatario()
                + " | Asunto: " + solicitud.asunto() + " | Mensaje: " + solicitud.mensaje());
    }
}