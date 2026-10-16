package com.stayhub.seguridad;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LimitadorLogin {
    static final int MAX_INTENTOS = 6;
    static final long VENTANA_SEGUNDOS = 600;
    private final Clock reloj;
    private final Map<String, Ventana> intentos = new ConcurrentHashMap<>();

    LimitadorLogin() { this(Clock.systemUTC()); }
    LimitadorLogin(Clock reloj) { this.reloj = reloj; }

    boolean permitir(String origen) {
        long ahora = Instant.now(reloj).getEpochSecond();
        String clave = origen == null || origen.isBlank() ? "desconocido" : origen;
        Ventana actual = intentos.compute(clave, (k, v) ->
                v == null || ahora - v.inicio >= VENTANA_SEGUNDOS
                        ? new Ventana(ahora, 1) : new Ventana(v.inicio, v.cantidad + 1));
        return actual.cantidad <= MAX_INTENTOS;
    }

    private record Ventana(long inicio, int cantidad) { }
}
