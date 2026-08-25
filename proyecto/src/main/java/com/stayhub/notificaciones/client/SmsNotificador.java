package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SmsNotificador implements NotificadorCanal {
    public void enviar(SolicitudNotificacionDTO solicitud) {
        // TODO: integrar con un proveedor real de SMS.
        System.out.println("[SMS] Para: " + solicitud.destinatario() + " | Mensaje: " + solicitud.mensaje());
    }
}