package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.model.SincronizacionProcesada;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Ejecuta una sincronización una sola vez por solicitudId. La clave se guarda
 * recién después de que la acción termina bien, así un fallo se reintenta y un
 * duplicado (reentrega del broker) no repite el efecto.
 */
@Stateless
public class SincronizacionIdempotente {

    /**
     * Acción a ejecutar una sola vez. Es una interfaz propia (y no Runnable) porque en
     * componentes EJB/MDB Sonar marca el uso de Runnable como manejo de hilos; acá no hay hilos.
     */
    @FunctionalInterface
    public interface Accion {
        void ejecutar();
    }

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void ejecutar(String solicitudId, Accion accion) {
        // Evita que dos consumidores procesen a la vez la misma solicitud (PostgreSQL).
        em.createNativeQuery("select cast(pg_advisory_xact_lock(cast(:clave as bigint)) as text)")
                .setParameter("clave", (long) ("sync:" + solicitudId).hashCode())
                .getSingleResult();

        if (em.find(SincronizacionProcesada.class, solicitudId) != null) {
            return;
        }

        accion.ejecutar();

        em.persist(new SincronizacionProcesada(solicitudId));
        em.flush();
    }
}