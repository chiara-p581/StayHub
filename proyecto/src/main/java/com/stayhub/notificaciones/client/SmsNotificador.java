package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;

@ApplicationScoped
public class SmsNotificador implements NotificadorCanal {
    private static final Logger LOG = Logger.getLogger(SmsNotificador.class.getName());

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.SMS;
    }

    @Override
    public void enviar(SolicitudNotificacionDTO solicitud) {
        // TODO: integrar con un proveedor real de SMS.
        LOG.info("Notificación SMS simulada procesada");
    }
}