package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.notificaciones.service.PlantillaEmail;
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

    @Inject
    private ConfiguracionMail configuracion;

    @Override
    public void onMessage(Message mensaje) {
        try {
            EventoUsuarioRegistrado evento = mensaje.getBody(EventoUsuarioRegistrado.class);
            String html = PlantillaEmail.armar(
                    "¡Te damos la bienvenida a StayHub!",
                    "Hola " + evento.nombre() + ",",
                    "Tu cuenta fue creada con éxito. Ya podés buscar hoteles, comparar precios entre canales "
                            + "y reservar tu próxima escapada en pocos pasos.",
                    null,
                    "Explorar hoteles",
                    configuracion.baseUrl() + "/index.html",
                    "Si no creaste esta cuenta, podés ignorar este mensaje.");

            servicio.enviar("bienvenida:" + evento.usuarioId(), new SolicitudNotificacionDTO(
                    evento.email(),
                    TipoEvento.BIENVENIDA,
                    CanalNotificacion.EMAIL,
                    "¡Bienvenido a StayHub, " + evento.nombre() + "!",
                    html));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo procesar el evento de usuario registrado", ex);
        }
    }
}