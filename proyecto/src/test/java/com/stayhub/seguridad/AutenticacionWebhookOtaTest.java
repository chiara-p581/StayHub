package com.stayhub.seguridad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class AutenticacionWebhookOtaTest {
    private static final String SECRETO = "0123456789abcdef0123456789abcdef";
    private static final long AHORA = 1_800_000_000L;

    @AfterEach void limpiar() { System.clearProperty("stayhub.canales.booking.webhook-secret"); }

    @Test void aceptaFirmaValidaYRechazaReplay() throws Exception {
        System.setProperty("stayhub.canales.booking.webhook-secret", SECRETO);
        var auth = new AutenticacionWebhookOta(Clock.fixed(Instant.ofEpochSecond(AHORA), ZoneOffset.UTC));
        byte[] cuerpo = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        String ruta = "canales-externos/otas/BOOKING/reservas";
        String nonce = "nonce-unico-123456";
        String firma = firmar(Long.toString(AHORA), nonce, "POST", ruta, cuerpo);

        assertTrue(auth.validar("BOOKING", "POST", ruta, cuerpo, Long.toString(AHORA), nonce, firma));
        assertFalse(auth.validar("BOOKING", "POST", ruta, cuerpo, Long.toString(AHORA), nonce, firma));
    }

    @Test void rechazaFirmaVencidaOCuerpoAlterado() throws Exception {
        System.setProperty("stayhub.canales.booking.webhook-secret", SECRETO);
        var auth = new AutenticacionWebhookOta(Clock.fixed(Instant.ofEpochSecond(AHORA), ZoneOffset.UTC));
        byte[] cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
        String ruta = "canales-externos/otas/BOOKING/reservas";
        String firma = firmar(Long.toString(AHORA - 301), "nonce-vencido-1234", "POST", ruta, cuerpo);
        assertFalse(auth.validar("BOOKING", "POST", ruta, cuerpo, Long.toString(AHORA - 301),
                "nonce-vencido-1234", firma));

        String firmaOriginal = firmar(Long.toString(AHORA), "nonce-alterado-123", "POST", ruta, cuerpo);
        assertFalse(auth.validar("BOOKING", "POST", ruta, "alterado".getBytes(StandardCharsets.UTF_8),
                Long.toString(AHORA), "nonce-alterado-123", firmaOriginal));
    }

    private String firmar(String timestamp, String nonce, String metodo, String ruta, byte[] cuerpo) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cuerpo));
        String canonico = timestamp + "\n" + nonce + "\n" + metodo + "\n" + ruta + "\n" + hash;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonico.getBytes(StandardCharsets.UTF_8)));
    }
}
