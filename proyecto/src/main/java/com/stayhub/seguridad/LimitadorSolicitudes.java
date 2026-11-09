package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;

/**
 * Ventanas compartidas entre nodos y persistentes entre reinicios. En pruebas
 * unitarias, donde no existe contenedor JPA, conserva un fallback en memoria.
 */
@ApplicationScoped
public class LimitadorSolicitudes {
    private record Ventana(long inicio, int cantidad) {}
    private final Map<String, Ventana> ventanas = new HashMap<>();
    @PersistenceContext(unitName = "StayHubPU")
    EntityManager em;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean permitir(String clave, int maximo, long ahora) {
        return incrementar(clave, ahora, 60_000) <= maximo;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int incrementar(String clave, long ahora, long ventanaMs) {
        if (em != null) {
            Number cantidad = (Number) em.createNativeQuery("""
                    INSERT INTO limite_solicitud (clave, ventana_inicio, cantidad, expira_en)
                    VALUES (?1, ?2, 1, ?3)
                    ON CONFLICT (clave) DO UPDATE SET
                      ventana_inicio = CASE WHEN limite_solicitud.expira_en <= ?2 THEN ?2 ELSE limite_solicitud.ventana_inicio END,
                      cantidad = CASE WHEN limite_solicitud.expira_en <= ?2 THEN 1 ELSE limite_solicitud.cantidad + 1 END,
                      expira_en = CASE WHEN limite_solicitud.expira_en <= ?2 THEN ?3 ELSE limite_solicitud.expira_en END
                    RETURNING cantidad
                    """).setParameter(1, clave).setParameter(2, ahora)
                    .setParameter(3, ahora + ventanaMs).getSingleResult();
            return cantidad.intValue();
        }
        return incrementarMemoria(clave, ahora, ventanaMs);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int cantidad(String clave, long ahora) {
        if (em != null) {
            em.createNativeQuery("DELETE FROM limite_solicitud WHERE clave = ?1 AND expira_en <= ?2")
                    .setParameter(1, clave).setParameter(2, ahora).executeUpdate();
            Object valor = em.createNativeQuery("SELECT cantidad FROM limite_solicitud WHERE clave = ?1")
                    .setParameter(1, clave).getResultStream().findFirst().orElse(0);
            return valor instanceof Number n ? n.intValue() : 0;
        }
        synchronized (this) {
            Ventana v = ventanas.get(clave);
            if (v == null || ahora - v.inicio() >= 900_000) return 0;
            return v.cantidad();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void eliminar(String clave) {
        if (em != null) em.createNativeQuery("DELETE FROM limite_solicitud WHERE clave = ?1")
                .setParameter(1, clave).executeUpdate();
        else synchronized (this) { ventanas.remove(clave); }
    }

    private synchronized int incrementarMemoria(String clave, long ahora, long ventanaMs) {
        ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= ventanaMs);
        Ventana v = ventanas.get(clave);
        if (v == null) {
            if (ventanas.size() >= 10000) return Integer.MAX_VALUE;
            ventanas.put(clave, new Ventana(ahora, 1)); return 1;
        }
        int cantidad = v.cantidad() + 1;
        ventanas.put(clave, new Ventana(v.inicio(), cantidad));
        return cantidad;
    }
}
