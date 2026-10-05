package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.usuarios.messaging.EventoUsuarioRegistrado;
import com.stayhub.usuarios.messaging.PublicadorEventoUsuario;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "stayhub-email-usuarios"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "email-bienvenida"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1"),
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = PublicadorEventoUsuario.JNDI_TOPICO),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic")
})
public class ConsumidorEventoUsuarioRegistrado implements MessageListener {

    @Inject
    private EnvioCorreoIdempotente servicio;

    @Override
    public void onMessage(Message mensaje) {
        try {
            EventoUsuarioRegistrado evento = mensaje.getBody(EventoUsuarioRegistrado.class);
            servicio.enviar("bienvenida:" + evento.usuarioId(), new SolicitudNotificacionDTO(
                    evento.email(),
                    TipoEvento.BIENVENIDA,
                    CanalNotificacion.EMAIL,
                    "¡Bienvenido a StayHub, " + evento.nombre() + "!",
                    "Tu cuenta fue creada con éxito. Ya podés empezar a buscar tu próxima escapada."));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo procesar el evento de usuario registrado", ex);
        }
    }
}