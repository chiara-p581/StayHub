package com.stayhub.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Casos del Eje D (Clase 15): "JWT vencido, con firma alterada, con alg=none o de otro
 * emisor → 401". Acá se prueba la validación; el 401 lo da AutenticacionFilter.
 */
class TokenJwtTest {

    private static final byte[] CLAVE = "clave-de-prueba-de-32-bytes-o-mas!!".getBytes(StandardCharsets.UTF_8);
    private static final long AHORA = 1_800_000_000_000L;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private static TokenJwt tokensEn(long millis) {
        return new TokenJwt(CLAVE, Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC));
    }

    private static String firmado(String cabecera, String payload, byte[] clave) throws Exception {
        String datos = B64.encodeToString(cabecera.getBytes(StandardCharsets.UTF_8)) + "."
                + B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave, "HmacSHA256"));
        return datos + "." + B64.encodeToString(mac.doFinal(datos.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String payload(String iss, String aud, long iat, long exp, long authTime, String rol, String jti) {
        return "{\"iss\":\"" + iss + "\",\"aud\":\"" + aud + "\",\"sub\":\"7\",\"rol\":\"" + rol
                + "\",\"mfa\":true,\"auth_time\":" + authTime / 1000 + ",\"reauth\":0,\"iat\":" + iat / 1000
                + ",\"exp\":" + exp / 1000 + ",\"jti\":\"" + jti + "\"}";
    }

    private static final String HS256 = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    @Test
    void tokenEmitidoEsValidoYConservaLaIdentidad() {
        TokenJwt tokens = tokensEn(AHORA);
        CredencialActual c = tokens.validar(tokens.emitir(7L, "HUESPED", true, AHORA, AHORA));
        assertEquals(7L, c.usuarioId());
        assertEquals("HUESPED", c.rol());
        assertTrue(c.mfaVerificada());
        assertEquals(AHORA + TokenJwt.DURACION_MS, c.expiraEn());
        assertFalse(c.admin());
    }

    @Test
    void tokenVencidoEsInvalido() {
        String token = tokensEn(AHORA).emitir(7L, "HUESPED", true, AHORA, 0L);
        TokenJwt quinceMinutosDespues = tokensEn(AHORA + TokenJwt.DURACION_MS);
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> quinceMinutosDespues.validar(token));
        assertEquals(7L, tokensEn(AHORA + TokenJwt.DURACION_MS - 1000).validar(token).usuarioId());
    }

    @Test
    void firmaAlteradaEsInvalida() {
        String token = tokensEn(AHORA).emitir(7L, "HUESPED", true, AHORA, 0L);
        String alterado = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(alterado));
    }

    @Test
    void payloadAlteradoParaSerAdminEsInvalido() {
        String token = tokensEn(AHORA).emitir(7L, "HUESPED", true, AHORA, 0L);
        String[] p = token.split("\\.");
        String adminFalso = B64.encodeToString(payload("stayhub", "stayhub-api", AHORA, AHORA + 60_000, AHORA, "ADMIN", "x")
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(p[0] + "." + adminFalso + "." + p[2]));
    }

    @Test
    void algNoneEsInvalido() {
        String cabecera = B64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String datos = B64.encodeToString(payload("stayhub", "stayhub-api", AHORA, AHORA + 60_000, AHORA, "ADMIN", "n")
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(cabecera + "." + datos + "."));
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(cabecera + "." + datos + ".x"));
    }

    @Test
    void otroAlgoritmoEsInvalidoAunqueLaFirmaHmacSeaCorrecta() throws Exception {
        String token = firmado("{\"alg\":\"HS512\",\"typ\":\"JWT\"}",
                payload("stayhub", "stayhub-api", AHORA, AHORA + 60_000, AHORA, "HUESPED", "a"), CLAVE);
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(token));
    }

    @Test
    void otroEmisorOtraAudienciaYOtraClaveSonInvalidos() throws Exception {
        TokenJwt tokens = tokensEn(AHORA);
        String otroEmisor = firmado(HS256, payload("https://otro-emisor", "stayhub-api", AHORA, AHORA + 60_000, AHORA, "HUESPED", "e"), CLAVE);
        String otraAudiencia = firmado(HS256, payload("stayhub", "otra-api", AHORA, AHORA + 60_000, AHORA, "HUESPED", "a"), CLAVE);
        String otraClave = firmado(HS256, payload("stayhub", "stayhub-api", AHORA, AHORA + 60_000, AHORA, "HUESPED", "k"),
                "otra-clave-distinta-de-32-bytes-o-mas".getBytes(StandardCharsets.UTF_8));
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokens.validar(otroEmisor));
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokens.validar(otraAudiencia));
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokens.validar(otraClave));
    }

    @Test
    void tokenConVidaMayorA15MinutosEsInvalido() throws Exception {
        String largo = firmado(HS256, payload("stayhub", "stayhub-api", AHORA, AHORA + 3_600_000, AHORA, "HUESPED", "l"), CLAVE);
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(largo));
    }

    @Test
    void sesionDeMasDe8HorasDesdeElLoginEsInvalida() {
        long login = AHORA - TokenJwt.DURACION_MAXIMA_SESION_MS - 1000;
        String token = tokensEn(AHORA).emitir(7L, "HUESPED", true, login, 0L);
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(token));
    }

    @Test
    void tokenRevocadoEnElLogoutEsInvalido() {
        TokenJwt tokens = tokensEn(AHORA);
        String token = tokens.emitir(7L, "HUESPED", true, AHORA, 0L);
        tokens.revocar(tokens.validar(token));
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokens.validar(token));
        assertEquals(7L, tokens.validar(tokens.emitir(7L, "HUESPED", true, AHORA, 0L)).usuarioId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "basura", "a.b", "a.b.c.d", "e30.e30.", "e30.e30.e30"})
    void tokensMalFormadosSonInvalidos(String token) {
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(token));
    }

    @Test
    void rolDesconocidoNoSeEmiteNiSeAcepta() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> tokensEn(AHORA).emitir(7L, "ROOT", true, AHORA, 0L));
        String root = firmado(HS256, payload("stayhub", "stayhub-api", AHORA, AHORA + 60_000, AHORA, "ROOT", "r"), CLAVE);
        assertThrows(TokenJwt.TokenInvalidoException.class, () -> tokensEn(AHORA).validar(root));
    }

    @Test
    void claveCortaSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> new TokenJwt(new byte[16], Clock.systemUTC()));
    }
}