package com.stayhub.pagos.config;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfiguracionPagos {

    public String urlPasarela() { return requerida("stayhub.pagos.pasarela.url"); }

    public String tokenPasarela() { return opcional("stayhub.pagos.pasarela.token"); }

    private String requerida(String clave) {
        String valor = opcional(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta configurar " + clave);
        }
        return valor;
    }

    private String opcional(String clave) {
        String valor = System.getProperty(clave);
        return valor != null ? valor : System.getenv(clave.toUpperCase().replace('.', '_'));
    }
}