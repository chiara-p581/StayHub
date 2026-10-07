package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.config.ConfiguracionMail;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.service.PlantillaEmail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.util.Properties;

@ApplicationScoped
public class EmailNotificador implements NotificadorCanal {

    @Inject
    private ConfiguracionMail configuracion;

    @Override
    public void enviar(SolicitudNotificacionDTO solicitud) {
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
            mensaje.setHeader("Content-Language", "es");

            // Si el mensaje ya viene en HTML lo usamos; si es texto simple
            // (por ejemplo, los avisos de overbooking) lo metemos en la plantilla.
            String html = PlantillaEmail.esHtml(solicitud.mensaje())
                    ? solicitud.mensaje()
                    : PlantillaEmail.generico(solicitud.asunto(), solicitud.mensaje());

            // multipart/alternative: versión texto (respaldo) + versión HTML (la que se ve)
            MimeBodyPart parteTexto = new MimeBodyPart();
            parteTexto.setText(PlantillaEmail.aTextoPlano(html), "UTF-8");

            MimeBodyPart parteHtml = new MimeBodyPart();
            parteHtml.setContent(html, "text/html; charset=UTF-8");

            MimeMultipart contenido = new MimeMultipart("alternative");
            contenido.addBodyPart(parteTexto);
            contenido.addBodyPart(parteHtml);
            mensaje.setContent(contenido);

            Transport.send(mensaje);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo enviar el correo",
                    e
            );
        }
    }
}