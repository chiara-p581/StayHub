package com.stayhub.notificaciones.config;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

@ApplicationScoped
public class ConfiguracionMail {

    public String valor(String clave, String defecto) {
        String valor = System.getProperty(clave);

        if (valor == null || valor.isBlank()) {
            String variable = clave.toUpperCase(Locale.ROOT)
                    .replace('.', '_')
                    .replace('-', '_');

            valor = System.getenv(variable);
        }

        if (valor != null && !valor.isBlank()) {
            return valor;
        }

        Path archivo = Path.of(
                System.getProperty(
                        "stayhub.mail.config",
                        Path.of(
                                System.getProperty("user.home"),
                                ".stayhub",
                                "mail.properties"
                        ).toString()
                )
        );

        Properties propiedades = new Properties();

        if (Files.exists(archivo)) {
            try (InputStream entrada = Files.newInputStream(archivo)) {
                propiedades.load(entrada);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "No se pudo leer la configuración externa del correo",
                        e
                );
            }
        }

        valor = propiedades.getProperty(clave);

        return valor == null || valor.isBlank() ? defecto : valor;
    }

    private String requerida(String clave) {
        String valor = valor(clave, null);

        if (valor == null) {
            throw new IllegalStateException("Falta configurar " + clave);
        }

        return valor;
    }

    public String remitente() {
        return requerida("stayhub.mail.remitente");
    }

    public String password() {
        return requerida("stayhub.mail.password");
    }

    public String host() {
        return valor("stayhub.mail.host", "smtp.gmail.com");
    }

    public String puerto() {
        return valor("stayhub.mail.port", "587");
    }

    public String baseUrl() {
        return valor(
                "stayhub.web.base-url",
                "http://localhost:8080/StayHub"
        ).replaceAll("/+$", "");
    }
}