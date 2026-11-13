package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Emite y valida los JWT de acceso de StayHub (HS256).
 *
 * <p>Reglas (criterios SEC-W03/W04 de la Clase 11 y Eje D de la Clase 15):</p>
 * <ul>
 *   <li>Solo se acepta {@code alg = HS256}: {@code none} u otro algoritmo → inválido.</li>
 *   <li>La firma se compara en tiempo constante; un token alterado → inválido.</li>
 *   <li>Emisor ({@code iss}) y audiencia ({@code aud}) tienen que ser los de StayHub.</li>
 *   <li>Vence a los 15 minutos ({@code exp}); además, ninguna sesión dura más de 8 horas
 *       desde el login ({@code auth_time}).</li>
 *   <li>En el logout el token queda revocado por su {@code jti}.</li>
 * </ul>
 *
 * <p>La clave se lee de la propiedad {@code stayhub.jwt.secreto} o de la variable
 * {@code STAYHUB_JWT_SECRETO} (Base64, 32 bytes o más). Nunca se guarda en el repo:
 * si no está configurada se genera una aleatoria al arrancar, y los tokens emitidos
 * dejan de valer cuando se reinicia WildFly (fallar de forma segura).</p>
 *
 * <p>GRASP: Fabricación pura y Experto (sabe todo lo que hace válido a un token).
 * SOLID: responsabilidad única; el resto del sistema solo ve {@link CredencialActual}.</p>
 */
@ApplicationScoped
public class TokenJwt {

    public static final String COOKIE = "STAYHUB_TOKEN";
    static final String EMISOR = "stayhub";
    static final String AUDIENCIA = "stayhub-api";
    static final long DURACION_MS = 15 * 60_000L;
    static final long DURACION_MAXIMA_SESION_MS = 8L * 60 * 60_000L;
    static final long RENOVAR_ANTES_DE_MS = 5 * 60_000L;
    private static final long TOLERANCIA_RELOJ_MS = 30_000L;
    private static final int LARGO_MAXIMO = 4096;
    private static final String CABECERA = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Logger LOG = Logger.getLogger(TokenJwt.class.getName());
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final Map<String, Long> REVOCADOS = new ConcurrentHashMap<>();

    private final byte[] clave;
    private final Clock reloj;

    public TokenJwt() {
        this(ClaveConfigurada.VALOR, Clock.systemUTC());
    }

    TokenJwt(byte[] clave, Clock reloj) {
        if (clave == null || clave.length < 32) throw new IllegalArgumentException("La clave JWT debe tener 32 bytes o más");
        this.clave = clave.clone();
        this.reloj = reloj;
    }

    /** Emite un token nuevo para una persona que acaba de autenticarse. */
    public String emitir(Long usuarioId, String rol, boolean mfaVerificada, long autenticadoEn, long reautenticadoEn) {
        if (usuarioId == null || usuarioId <= 0 || !rolValido(rol)) throw new IllegalArgumentException("Identidad inválida");
        long ahora = reloj.millis();
        String payload = Json.createObjectBuilder()
                .add("iss", EMISOR)
                .add("aud", AUDIENCIA)
                .add("sub", String.valueOf(usuarioId))
                .add("rol", rol)
                .add("mfa", mfaVerificada)
                .add("auth_time", autenticadoEn / 1000)
                .add("reauth", reautenticadoEn / 1000)
                .add("iat", ahora / 1000)
                .add("exp", (ahora + DURACION_MS) / 1000)
                .add("jti", UUID.randomUUID().toString())
                .build().toString();
        String datos = B64.encodeToString(CABECERA.getBytes(StandardCharsets.UTF_8)) + "."
                + B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return datos + "." + B64.encodeToString(firmar(datos));
    }

    /** Emite un token nuevo con los mismos datos que otro, pero con los cambios indicados. */
    public String reemitir(CredencialActual base, String rol, boolean mfaVerificada, long reautenticadoEn) {
        return emitir(base.usuarioId(), rol, mfaVerificada, base.autenticadoEn(), reautenticadoEn);
    }

    /** Valida el token completo y devuelve la identidad. Cualquier problema → {@link TokenInvalidoException}. */
    public CredencialActual validar(String token) {
        if (token == null || token.isBlank() || token.length() > LARGO_MAXIMO) throw new TokenInvalidoException();
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3 || partes[2].isEmpty()) throw new TokenInvalidoException();
        try {
            JsonObject cabecera = leer(partes[0]);
            if (!"HS256".equals(texto(cabecera, "alg"))) throw new TokenInvalidoException();
            if (cabecera.containsKey("typ") && !"JWT".equals(texto(cabecera, "typ"))) throw new TokenInvalidoException();
            if (cabecera.containsKey("crit")) throw new TokenInvalidoException();
            byte[] firmaRecibida = B64D.decode(partes[2]);
            if (!MessageDigest.isEqual(firmar(partes[0] + "." + partes[1]), firmaRecibida)) throw new TokenInvalidoException();

            JsonObject datos = leer(partes[1]);
            long ahora = reloj.millis();
            long exp = segundos(datos, "exp") * 1000;
            long iat = segundos(datos, "iat") * 1000;
            long autenticadoEn = segundos(datos, "auth_time") * 1000;
            long reautenticadoEn = segundos(datos, "reauth") * 1000;
            String jti = texto(datos, "jti");
            String rol = texto(datos, "rol");
            if (!EMISOR.equals(texto(datos, "iss")) || !AUDIENCIA.equals(texto(datos, "aud"))) throw new TokenInvalidoException();
            if (exp <= ahora || iat > ahora + TOLERANCIA_RELOJ_MS || exp - iat > DURACION_MS) throw new TokenInvalidoException();
            if (autenticadoEn > ahora + TOLERANCIA_RELOJ_MS || ahora - autenticadoEn > DURACION_MAXIMA_SESION_MS) throw new TokenInvalidoException();
            if (jti == null || jti.isBlank() || REVOCADOS.containsKey(jti)) throw new TokenInvalidoException();
            if (!rolValido(rol)) throw new TokenInvalidoException();
            long usuarioId = Long.parseLong(texto(datos, "sub"));
            if (usuarioId <= 0) throw new TokenInvalidoException();
            boolean mfa = datos.get("mfa") == JsonValue.TRUE;
            return new CredencialActual(usuarioId, rol, mfa, autenticadoEn, reautenticadoEn, jti, exp);
        } catch (TokenInvalidoException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TokenInvalidoException();
        }
    }

    /** Revoca el token (logout). Sigue revocado hasta que vence. */
    public void revocar(CredencialActual credencial) {
        if (credencial == null) return;
        long ahora = reloj.millis();
        REVOCADOS.entrySet().removeIf(e -> e.getValue() <= ahora);
        REVOCADOS.put(credencial.jti(), credencial.expiraEn());
    }

    /** Toma el token del header {@code Authorization: Bearer} o, si no hay, de la cookie. */
    public static String extraer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) return header.substring(7).trim();
        if (header != null && !header.isBlank()) return header; // esquema desconocido: se valida y falla
        var cookies = request.getCookies();
        if (cookies == null) return null;
        for (var cookie : cookies) if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    /** {@code true} si el pedido usa {@code Authorization} y no la cookie (cliente de API, sin CSRF). */
    public static boolean usaHeaderAuthorization(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) return false;
        var cookies = request.getCookies();
        if (cookies != null) for (var cookie : cookies) if (COOKIE.equals(cookie.getName())) return false;
        return true;
    }

    /** Guarda el token en una cookie HttpOnly / Secure / SameSite (SEC-W05). */
    public static void escribirCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", cookie(request, token, DURACION_MS / 1000));
    }

    /** Borra la cookie del token (logout). */
    public static void borrarCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookie(request, "", 0));
    }

    private static String cookie(HttpServletRequest request, String valor, long maxAge) {
        String ruta = request.getContextPath() == null || request.getContextPath().isBlank() ? "/" : request.getContextPath();
        return COOKIE + "=" + valor + "; Path=" + ruta + "; Max-Age=" + maxAge + "; HttpOnly; SameSite=Lax"
                + (SeguridadHttpFilter.httpLocalHabilitado() ? "" : "; Secure");
    }

    private byte[] firmar(String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(datos.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo firmar el token", ex);
        }
    }

    private static JsonObject leer(String parte) {
        String json = new String(B64D.decode(parte), StandardCharsets.UTF_8);
        try (var lector = Json.createReader(new StringReader(json))) {
            return lector.readObject();
        }
    }

    private static String texto(JsonObject json, String campo) {
        JsonValue valor = json.get(campo);
        return valor instanceof JsonString s ? s.getString() : null;
    }

    private static long segundos(JsonObject json, String campo) {
        JsonValue valor = json.get(campo);
        if (!(valor instanceof JsonNumber n) || !n.isIntegral()) throw new TokenInvalidoException();
        return n.longValueExact();
    }

    private static boolean rolValido(String rol) {
        return Roles.HUESPED.equals(rol) || Roles.ADMIN.equals(rol);
    }

    /** Token ausente, vencido, alterado, revocado o con algoritmo/emisor incorrecto. */
    public static class TokenInvalidoException extends RuntimeException {
        public TokenInvalidoException() { super("Token inválido", null, false, false); }
    }

    /** Clave única para toda la aplicación (se calcula una sola vez). */
    private static final class ClaveConfigurada {
        static final byte[] VALOR = cargar();

        private static byte[] cargar() {
            String valor = System.getProperty("stayhub.jwt.secreto", System.getenv("STAYHUB_JWT_SECRETO"));
            if (valor != null && !valor.isBlank()) {
                byte[] clave = Base64.getDecoder().decode(valor.trim());
                if (clave.length < 32) throw new IllegalStateException("stayhub.jwt.secreto debe tener 32 bytes o más (Base64)");
                return clave;
            }
            LOG.warning("stayhub.jwt.secreto no está configurado: se usa una clave aleatoria y los tokens se invalidan al reiniciar.");
            byte[] clave = new byte[32];
            new SecureRandom().nextBytes(clave);
            return clave;
        }
    }
}