package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limita exclusivamente credenciales fallidas, tanto por cuenta como por IP. */
@ApplicationScoped
public class ProteccionLogin {
    static final int MAX_FALLOS = 5;
    static final long VENTANA_SEGUNDOS = 900;
    private final Map<String, Ventana> fallos = new ConcurrentHashMap<>();
    private final Clock reloj;

    public ProteccionLogin() { this(Clock.systemUTC()); }
    ProteccionLogin(Clock reloj) { this.reloj = reloj; }

    public void verificar(String email, String ip) {
        long ahora = Instant.now(reloj).getEpochSecond();
        if (bloqueada(claveCuenta(email), ahora) || bloqueada(claveIp(ip), ahora))
            throw new SeguridadException(429, "Demasiados intentos de acceso. Intentá nuevamente en 15 minutos");
    }

    public void registrarFallo(String email, String ip) {
        long ahora = Instant.now(reloj).getEpochSecond();
        incrementar(claveCuenta(email), ahora);
        incrementar(claveIp(ip), ahora);
    }

    public void registrarExito(String email, String ip) {
        fallos.remove(claveCuenta(email));
        fallos.remove(claveIp(ip));
    }

    private boolean bloqueada(String clave, long ahora) {
        Ventana v = fallos.get(clave);
        if (v == null) return false;
        if (ahora - v.inicio >= VENTANA_SEGUNDOS) { fallos.remove(clave, v); return false; }
        return v.cantidad >= MAX_FALLOS;
    }

    private void incrementar(String clave, long ahora) {
        fallos.compute(clave, (k, v) -> v == null || ahora - v.inicio >= VENTANA_SEGUNDOS
                ? new Ventana(ahora, 1) : new Ventana(v.inicio, v.cantidad + 1));
    }

    private String claveCuenta(String email) { return "cuenta:" + hash(email == null ? "" : email.strip().toLowerCase(Locale.ROOT)); }
    private String claveIp(String ip) { return "ip:" + hash(ip == null ? "desconocida" : ip); }
    private String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private record Ventana(long inicio, int cantidad) { }
}