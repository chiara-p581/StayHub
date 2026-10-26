package com.stayhub.seguridad;

import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Contadores atómicos compartidos por todos los nodos y persistentes a reinicios. */
@Stateless
public class ContadoresSeguridad {
    @PersistenceContext(unitName = "StayHubPU")
    EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int incrementar(String claveHash, long ahora, long ventanaMs) {
        long corte = ahora - ventanaMs;
        long vence = ahora + ventanaMs;
        Object valor = em.createNativeQuery("""
                INSERT INTO seguridad_contador (clave_hash, ventana_inicio, cantidad, vence)
                VALUES (?1, ?2, 1, ?3)
                ON CONFLICT (clave_hash) DO UPDATE SET
                  cantidad = CASE WHEN seguridad_contador.ventana_inicio <= ?4
                                  THEN 1 ELSE seguridad_contador.cantidad + 1 END,
                  ventana_inicio = CASE WHEN seguridad_contador.ventana_inicio <= ?4
                                        THEN ?2 ELSE seguridad_contador.ventana_inicio END,
                  vence = ?3
                RETURNING cantidad
                """).setParameter(1, claveHash).setParameter(2, ahora).setParameter(3, vence)
                .setParameter(4, corte).getSingleResult();
        return ((Number) valor).intValue();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean bloqueada(String claveHash, int maximo, long ahora, long ventanaMs) {
        Number cantidad = (Number) em.createNativeQuery("""
                SELECT COALESCE(MAX(cantidad), 0) FROM seguridad_contador
                 WHERE clave_hash = ?1 AND ventana_inicio > ?2 AND vence > ?3
                """).setParameter(1, claveHash).setParameter(2, ahora - ventanaMs)
                .setParameter(3, ahora).getSingleResult();
        return cantidad.intValue() >= maximo;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void eliminar(String claveHash) {
        em.createNativeQuery("DELETE FROM seguridad_contador WHERE clave_hash = ?1")
                .setParameter(1, claveHash).executeUpdate();
    }

    @Schedule(hour = "*", minute = "17", persistent = false)
    public void limpiarExpirados() {
        em.createNativeQuery("DELETE FROM seguridad_contador WHERE vence < ?1")
                .setParameter(1, System.currentTimeMillis()).executeUpdate();
    }
}
