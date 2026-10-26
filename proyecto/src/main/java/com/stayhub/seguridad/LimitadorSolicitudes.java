package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/** Ventana de un minuto, almacenamiento acotado. Rechaza al saturarse, sin crear claves ilimitadas. */
@ApplicationScoped
public class LimitadorSolicitudes {
    private record Ventana(long inicio, int cantidad) {}
    private final Map<String, Ventana> ventanas = new HashMap<>();
    @Inject ContadoresSeguridad persistente;

    public synchronized boolean permitir(String clave, int maximo, long ahora) {
        return permitir(clave, maximo, ahora, 60_000);
    }

    public synchronized boolean permitir(String clave, int maximo, long ahora, long ventanaMs) {
        String hash = hash(clave);
        if (persistente != null) return persistente.incrementar(hash, ahora, ventanaMs) <= maximo;
        ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= ventanaMs);
        Ventana v = ventanas.get(hash);
        if (v == null) {
            if (ventanas.size() >= 10000) return false;
            ventanas.put(hash, new Ventana(ahora, 1)); return true;
        }
        if (v.cantidad() >= maximo) return false;
        ventanas.put(hash, new Ventana(v.inicio(), v.cantidad() + 1)); return true;
    }

    public synchronized boolean bloqueada(String clave, int maximo, long ahora, long ventanaMs) {
        String hash = hash(clave);
        if (persistente != null) return persistente.bloqueada(hash, maximo, ahora, ventanaMs);
        Ventana v = ventanas.get(hash);
        if (v == null) return false;
        if (ahora - v.inicio() >= ventanaMs) { ventanas.remove(hash); return false; }
        return v.cantidad() >= maximo;
    }

    public synchronized void olvidar(String clave) {
        String hash = hash(clave);
        if (persistente != null) persistente.eliminar(hash);
        else ventanas.remove(hash);
    }

    private String hash(String clave) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(clave).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
