package com.stayhub.notificaciones.service;

import com.stayhub.notificaciones.client.*;
import com.stayhub.notificaciones.contrato.ServicioDeNotificaciones;
import com.stayhub.notificaciones.dto.ResultadoEnvioNotificacionDTO;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.exception.CodigoErrorNotificacion;
import com.stayhub.notificaciones.exception.NotificacionException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
public class ServicioDeNotificacionesImpl implements ServicioDeNotificaciones {

    @Inject EmailNotificador emailNotificador;
    @Inject SmsNotificador smsNotificador;
    @Inject PushNotificador pushNotificador;

    @Override
    public ResultadoEnvioNotificacionDTO enviar(SolicitudNotificacionDTO solicitud) {
        validar(solicitud);

        NotificadorCanal notificador = switch (solicitud.canalPreferido()) {
            case EMAIL -> emailNotificador;
            case SMS -> smsNotificador;
            case PUSH -> pushNotificador;
        };

        try {
            notificador.enviar(solicitud);
        } catch (RuntimeException ex) {
            throw new NotificacionException(CodigoErrorNotificacion.ERROR_ENVIO,
                    "No se pudo enviar la notificación por " + solicitud.canalPreferido(), ex);
        }

        return ResultadoEnvioNotificacionDTO.exitoso(solicitud.canalPreferido());
    }

    private void validar(SolicitudNotificacionDTO solicitud) {
        if (solicitud == null || solicitud.destinatario() == null || solicitud.destinatario().isBlank()
                || solicitud.canalPreferido() == null || solicitud.tipoEvento() == null)
            throw new NotificacionException(CodigoErrorNotificacion.SOLICITUD_INVALIDA,
                    "Faltan datos obligatorios (destinatario / canal / tipoEvento)");
    }
}