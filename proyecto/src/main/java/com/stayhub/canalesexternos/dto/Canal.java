package com.stayhub.canalesexternos.dto;

public enum Canal {
    BOOKING, EXPEDIA, AIRBNB, DESPEGAR, OTRO;

    public static Canal desde(String valor) {
        try {
            return Canal.valueOf(valor.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Canal OTA no soportado: " + valor, ex);
        }
    }
}
