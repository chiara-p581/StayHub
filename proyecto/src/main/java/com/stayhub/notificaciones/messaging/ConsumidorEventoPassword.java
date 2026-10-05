package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.usuarios.messaging.EventoPassword;
import com.stayhub.usuarios.messaging.PublicadorEventoPassword;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = PublicadorEventoPassword.JNDI_COLA),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class ConsumidorEventoPassword implements MessageListener {

    @Inject
    private EnvioCorreoIdempotente servicio;

    @Inject
    private ConfiguracionMail configuracion;

    @Override
    public void onMessage(Message mensaje) {
        try {
            EventoPassword evento = mensaje.getBody(EventoPassword.class);
            SolicitudNotificacionDTO dto = switch (evento.tipo()) {
                case SOLICITUD_RECUPERACION -> new SolicitudNotificacionDTO(
                        evento.email(), TipoEvento.RECUPERACION_PASSWORD, CanalNotificacion.EMAIL,
                        "Recuperá tu contraseña — StayHub",
                        "Hola " + evento.nombre() + ", abrí este enlace para restablecer tu contraseña: "
                                + configuracion.baseUrl() + "/reset-password.html#token=" + evento.token()
                                + " (válido por 1 hora). Si no lo pediste vos, ignorá este mensaje.");
                case PASSWORD_CAMBIADA -> new SolicitudNotificacionDTO(
                        evento.email(), TipoEvento.CAMBIO_PASSWORD, CanalNotificacion.EMAIL,
                        "Tu contraseña fue actualizada — StayHub",
                        "Hola " + evento.nombre() + ", te confirmamos que tu contraseña se cambió con éxito. "
                                + "Si no fuiste vos, contactanos de inmediato.");
            };
            servicio.enviar("password:" + mensaje.getJMSMessageID(), dto);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo procesar el evento de password", ex);
        }
    }
}