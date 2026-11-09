package com.stayhub.infraestructura.cache;

import com.stayhub.seguridad.LimitadorSolicitudes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Reserva cuota antes de usarla: otros nodos y reinicios nunca recuperan tokens
 * ya asignados. Perder tokens locales reduce disponibilidad, no aumenta el límite.
 * Exclusivo del catálogo público; las operaciones sensibles usan el limitador original.
 */
@ApplicationScoped
public class CuotasLecturasPublicas {
    private static final int BLOQUE = 512;
    private static final int MAX_IPS = 10_000;
    @Inject private ReservaCuotaPublica reservas;
    private final LimitadorSolicitudes sinContenedor = new LimitadorSolicitudes();
    private final Map<String, Ventana> ventanas = new HashMap<>();
    private static final class Ventana {
        int restantes;
        volatile long expira;
        boolean agotada;
        boolean reponiendo;
    }

    public boolean permitir(String ip, int limite, long ahora) {
        String clave = "global-publico:" + ip;
        // Solo para pruebas unitarias que construyen el filtro sin CDI.
        if (reservas == null) return sinContenedor.permitir(clave, limite, ahora);
        Ventana ventana;
        synchronized (this) {
            ventanas.entrySet().removeIf(e -> e.getValue().expira <= ahora);
            ventana = ventanas.get(clave);
            if (ventana == null) {
                if (ventanas.size() >= MAX_IPS) return false;
                ventana = new Ventana();
                ventana.expira = ahora + 60_000;
                ventanas.put(clave, ventana);
            }
        }
        boolean consumido = false;
        synchronized (ventana) {
            if (ventana.restantes > 0) {
                ventana.restantes--;
                consumido = true;
                if (ventana.restantes > BLOQUE / 2 || ventana.reponiendo || ventana.agotada) return true;
            } else {
                if (ventana.agotada) return false;
                long hasta = System.nanoTime() + 5_000_000_000L;
                while (ventana.reponiendo && ventana.restantes == 0) {
                    long espera = hasta - System.nanoTime();
                    if (espera <= 0) return false;
                    try { ventana.wait(Math.max(1, espera / 1_000_000)); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
                }
                if (ventana.restantes > 0) { ventana.restantes--; return true; }
                if (ventana.agotada) return false;
            }
            ventana.reponiendo = true;
        }
        // El acceso remoto queda fuera del monitor: otros requests pueden consumir
        // los permisos ya persistidos mientras esta única solicitud repone saldo.
        try {
            var bloque = reservas.reservar(clave, limite, BLOQUE, ahora);
            synchronized (ventana) {
                ventana.restantes += bloque.permisos();
                ventana.expira = bloque.expiraEn();
                ventana.agotada = bloque.permisos() == 0;
                if (!consumido && ventana.restantes > 0) { ventana.restantes--; return true; }
                return consumido;
            }
        } catch (RuntimeException ex) {
            // El permiso de esta solicitud ya estaba reservado; no se inventan tokens
            // si falla el anticipo. Sin saldo previamente reservado, se rechaza.
            if (consumido) return true;
            throw ex;
        } finally {
            synchronized (ventana) { ventana.reponiendo = false; ventana.notifyAll(); }
        }
    }
}
