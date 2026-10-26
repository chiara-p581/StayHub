package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@ApplicationScoped
class AutenticacionWebhookOta {
    static final long VENTANA_SEGUNDOS = 300;
    private final Clock reloj;
    @Inject LimitadorSolicitudes limitador;

    AutenticacionWebhookOta() { this(Clock.systemUTC()); }
    AutenticacionWebhookOta(Clock reloj) { this.reloj = reloj; }

    boolean validar(String canal, String metodo, String ruta, byte[] cuerpo,
                    String timestamp, String nonce, String firma) {
        if (canal == null || timestamp == null || nonce == null || firma == null
                || nonce.length() < 16 || nonce.length() > 128) return false;
        long instante;
        try { instante = Long.parseLong(timestamp); }
        catch (NumberFormatException ex) { return false; }
        long ahora = Instant.now(reloj).getEpochSecond();
        if (Math.abs(ahora - instante) > VENTANA_SEGUNDOS) return false;

        String secreto = secreto(canal);
        if (secreto == null || secreto.length() < 32) return false;
        String claveReplay = canal.toUpperCase() + ":" + nonce;

        String hashCuerpo = HexFormat.of().formatHex(sha256(cuerpo));
        String canonico = timestamp + "\n" + nonce + "\n" + metodo + "\n" + ruta + "\n" + hashCuerpo;
        byte[] esperada = hmac(secreto, canonico);
        byte[] recibida;
        try { recibida = HexFormat.of().parseHex(firma); }
        catch (IllegalArgumentException ex) { return false; }
        if (!MessageDigest.isEqual(esperada, recibida)) return false;
        if (limitador == null) limitador = new LimitadorSolicitudes();
        return limitador.permitir("webhook-replay:" + claveReplay, 1, ahora * 1000,
                VENTANA_SEGUNDOS * 2 * 1000);
    }

    private String secreto(String canal) {
        String clave = "stayhub.canales." + canal.toLowerCase() + ".webhook-secret";
        String valor = System.getProperty(clave);
        return valor != null ? valor : System.getenv(clave.toUpperCase().replace('.', '_').replace('-', '_'));
    }

    private byte[] hmac(String secreto, String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(valor.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) { throw new IllegalStateException("No se pudo validar la firma OTA", ex); }
    }

    private byte[] sha256(byte[] valor) {
        try { return MessageDigest.getInstance("SHA-256").digest(valor == null ? new byte[0] : valor); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
