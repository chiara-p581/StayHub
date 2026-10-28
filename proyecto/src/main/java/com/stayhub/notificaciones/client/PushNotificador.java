package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;

@ApplicationScoped
public class PushNotificador implements NotificadorCanal {
    private static final Logger LOG = Logger.getLogger(PushNotificador.class.getName());

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.PUSH;
    }

    @Override
    public void enviar(SolicitudNotificacionDTO solicitud) {
        // TODO: integrar con un proveedor real de push (FCM, APNs, etc.).
        LOG.info("Notificación push simulada procesada");
    }
}