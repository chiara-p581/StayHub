package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.servicioDeOverbooking.messaging.PublicadorSolicitudNotificacionAsincrona;
import com.stayhub.servicioDeOverbooking.messaging.SolicitudNotificacionAsincrona;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

@MessageDriven(
        activationConfig = {
                @ActivationConfigProperty(
                        propertyName = "destinationLookup",
                        propertyValue = PublicadorSolicitudNotificacionAsincrona.JNDI_COLA
                ),
                @ActivationConfigProperty(
                        propertyName = "destinationType",
                        propertyValue = "jakarta.jms.Queue"
                )
        }
)
public class ConsumidorSolicitudNotificacionAsincrona
        implements MessageListener {

    @Inject
    private EnvioCorreoIdempotente envio;

    @Override
    public void onMessage(Message mensaje) {
        try {
            SolicitudNotificacionAsincrona solicitud =
                    mensaje.getBody(SolicitudNotificacionAsincrona.class);

            SolicitudNotificacionDTO correo = new SolicitudNotificacionDTO(
                    solicitud.destinatario(),
                    solicitud.tipoEvento(),
                    solicitud.canalPreferido(),
                    solicitud.asunto(),
                    solicitud.mensaje()
            );

            envio.enviar(
                    "overbooking:" + mensaje.getJMSMessageID(),
                    correo
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo procesar el aviso de overbooking",
                    e
            );
        }
    }
}