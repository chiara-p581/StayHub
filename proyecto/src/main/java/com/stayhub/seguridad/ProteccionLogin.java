package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** Limita exclusivamente credenciales fallidas, tanto por cuenta como por IP. */
@ApplicationScoped
public class ProteccionLogin {
    static final int MAX_FALLOS = 5;
    static final long VENTANA_SEGUNDOS = 900;
    @Inject LimitadorSolicitudes limitador;
    private final Clock reloj;

    public ProteccionLogin() { this(Clock.systemUTC()); }
    ProteccionLogin(Clock reloj) { this.reloj = reloj; }

    private LimitadorSolicitudes limitador() {
        if (limitador == null) limitador = new LimitadorSolicitudes();
        return limitador;
    }

    public void verificar(String email, String ip) {
        long ahora = Instant.now(reloj).getEpochSecond();
        if (limitador().bloqueada(claveCuenta(email), MAX_FALLOS, ahora * 1000, VENTANA_SEGUNDOS * 1000)
                || limitador().bloqueada(claveIp(ip), MAX_FALLOS, ahora * 1000, VENTANA_SEGUNDOS * 1000))
            throw new SeguridadException(429, "Demasiados intentos de acceso. Intentá nuevamente en 15 minutos");
    }

    public void registrarFallo(String email, String ip) {
        long ahora = Instant.now(reloj).getEpochSecond();
        limitador().permitir(claveCuenta(email), Integer.MAX_VALUE, ahora * 1000, VENTANA_SEGUNDOS * 1000);
        limitador().permitir(claveIp(ip), Integer.MAX_VALUE, ahora * 1000, VENTANA_SEGUNDOS * 1000);
    }

    public void registrarExito(String email, String ip) {
        limitador().olvidar(claveCuenta(email));
        // Un acceso válido rehabilita esa cuenta, pero no borra evidencia de
        // ataques contra otras cuentas que compartan la misma dirección.
    }

    private String claveCuenta(String email) { return "login:cuenta:" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT)); }
    private String claveIp(String ip) { return "login:ip:" + (ip == null ? "desconocida" : ip); }
}
