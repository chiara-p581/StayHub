package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** TOTP RFC 6238 para cuentas administrativas; secretos fuera de la base y del repositorio. */
@ApplicationScoped
public class MfaTotp {
    private static final long PASO_SEGUNDOS = 30;
    @Inject LimitadorSolicitudes limitador;
    private final Clock reloj;

    public MfaTotp() { this(Clock.systemUTC()); }
    MfaTotp(Clock reloj) { this.reloj = reloj; }

    public void verificar(Long usuarioId, String codigo) {
        String secreto = secreto(usuarioId);
        if (secreto == null || secreto.isBlank())
            throw new SeguridadException(503, "La cuenta administrativa no tiene MFA configurado");
        if (codigo == null || !codigo.matches("[0-9]{6}")) fallo();
        byte[] clave;
        try { clave = base32(secreto); }
        catch (IllegalArgumentException ex) { throw new SeguridadException(503, "La configuración MFA no es válida"); }
        if (clave.length < 20) throw new SeguridadException(503, "La configuración MFA no es válida");
        long ahora = Instant.now(reloj).getEpochSecond();
        long paso = ahora / PASO_SEGUNDOS, coincidente = Long.MIN_VALUE;
        for (long candidato = paso - 1; candidato <= paso + 1; candidato++) {
            if (MessageDigest.isEqual(codigo(clave, candidato).getBytes(StandardCharsets.US_ASCII),
                    codigo.getBytes(StandardCharsets.US_ASCII))) coincidente = candidato;
        }
        if (coincidente == Long.MIN_VALUE) fallo();
        if (limitador == null) limitador = new LimitadorSolicitudes();
        if (!limitador.permitir("mfa:uso:" + usuarioId + ":" + coincidente, 1,
                ahora * 1000, 120_000))
            throw new SeguridadException(401, "Código MFA inválido o ya utilizado");
    }

    String secreto(Long usuarioId) {
        String propiedad = System.getProperty("stayhub.mfa.admin." + usuarioId + ".secret");
        if (propiedad != null) return propiedad;
        return System.getenv("STAYHUB_MFA_ADMIN_" + usuarioId + "_SECRET");
    }

    String codigo(String secretoBase32, long epochSeconds) {
        return codigo(base32(secretoBase32), epochSeconds / PASO_SEGUNDOS);
    }

    private String codigo(byte[] secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secreto, "HmacSHA1"));
            byte[] hmac = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
            int offset = hmac[hmac.length - 1] & 0x0f;
            int binario = ((hmac[offset] & 0x7f) << 24) | ((hmac[offset + 1] & 0xff) << 16)
                    | ((hmac[offset + 2] & 0xff) << 8) | (hmac[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binario % 1_000_000);
        } catch (Exception ex) { throw new IllegalStateException("No se pudo validar MFA", ex); }
    }

    private byte[] base32(String valor) {
        String limpio = valor.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (!limpio.matches("[A-Z2-7]+")) throw new IllegalArgumentException();
        byte[] salida = new byte[limpio.length() * 5 / 8];
        int buffer = 0, bits = 0, indice = 0;
        for (char c : limpio.toCharArray()) {
            int valor5 = c >= 'A' && c <= 'Z' ? c - 'A' : c - '2' + 26;
            buffer = (buffer << 5) | valor5; bits += 5;
            if (bits >= 8) { salida[indice++] = (byte) (buffer >> (bits -= 8)); }
        }
        return salida;
    }

    private void fallo() { throw new SeguridadException(401, "Código MFA inválido o ya utilizado"); }
}
