package com.stayhub.notificaciones.contrato;

import com.stayhub.notificaciones.dto.ResultadoEnvioNotificacionDTO;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;

public interface ServicioDeNotificaciones {
    ResultadoEnvioNotificacionDTO enviar(SolicitudNotificacionDTO solicitud);
}