package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PushNotificador implements NotificadorCanal {
    public void enviar(SolicitudNotificacionDTO solicitud) {
        // TODO: integrar con un proveedor real de push (FCM, APNs, etc.).
        System.out.println("[PUSH] Para: " + solicitud.destinatario() + " | Mensaje: " + solicitud.mensaje());
    }
}