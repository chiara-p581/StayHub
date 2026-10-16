package com.stayhub.seguridad;

import java.util.HashMap;
import java.util.Map;

/** Ventana de un minuto, almacenamiento acotado. Rechaza al saturarse, sin crear claves ilimitadas. */
public class LimitadorSolicitudes {
    private record Ventana(long inicio, int cantidad) {}
    private final Map<String, Ventana> ventanas = new HashMap<>();
    public synchronized boolean permitir(String clave, int maximo, long ahora) {
        ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= 60000);
        Ventana v = ventanas.get(clave);
        if (v == null) {
            if (ventanas.size() >= 10000) return false;
            ventanas.put(clave, new Ventana(ahora, 1)); return true;
        }
        if (v.cantidad() >= maximo) return false;
        ventanas.put(clave, new Ventana(v.inicio(), v.cantidad() + 1)); return true;
    }
}
