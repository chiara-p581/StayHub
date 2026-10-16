package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

@ApplicationScoped
public class EmailNotificador implements NotificadorCanal {
    private static final com.stayhub.seguridad.LimitadorSolicitudes ENVIOS = new com.stayhub.seguridad.LimitadorSolicitudes();

    @Inject
    private ConfiguracionMail configuracion;

    @Override
    public void enviar(SolicitudNotificacionDTO solicitud) {
        if (solicitud == null) throw new com.stayhub.seguridad.SeguridadException(400, "Correo requerido");
        com.stayhub.seguridad.Limites.email(solicitud.destinatario());
        com.stayhub.seguridad.Limites.texto(solicitud.asunto(), 200, true);
        com.stayhub.seguridad.Limites.texto(solicitud.mensaje(), 4000, true);
        if (!ENVIOS.permitir(solicitud.destinatario().toLowerCase(java.util.Locale.ROOT), 5, System.currentTimeMillis()))
            throw new com.stayhub.seguridad.SeguridadException(429, "Cuota de correo excedida");
        try {
            Properties propiedades = new Properties();

            propiedades.setProperty("mail.smtp.auth", "true");
            propiedades.setProperty("mail.smtp.starttls.enable", "true");
            propiedades.setProperty("mail.smtp.starttls.required", "true");
            propiedades.setProperty("mail.smtp.ssl.checkserveridentity", "true");

            propiedades.setProperty("mail.smtp.host", configuracion.host());
            propiedades.setProperty("mail.smtp.port", configuracion.puerto());

            propiedades.setProperty("mail.smtp.connectiontimeout", "10000");
            propiedades.setProperty("mail.smtp.timeout", "10000");
            propiedades.setProperty("mail.smtp.writetimeout", "10000");

            String usuario = configuracion.remitente();
            String password = configuracion.password();

            Session sesion = Session.getInstance(
                    propiedades,
                    new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(usuario, password);
                        }
                    }
            );

            MimeMessage mensaje = new MimeMessage(sesion);

            mensaje.setFrom(
                    new InternetAddress(usuario, "StayHub", "UTF-8")
            );

            InternetAddress destinatario = new InternetAddress(
                    solicitud.destinatario(),
                    true
            );

            destinatario.validate();

            mensaje.setRecipient(Message.RecipientType.TO, destinatario);
            mensaje.setSubject(solicitud.asunto(), "UTF-8");
            mensaje.setText(solicitud.mensaje(), "UTF-8");

            Transport.send(mensaje);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo enviar el correo",
                    e
            );
        }
    }
}