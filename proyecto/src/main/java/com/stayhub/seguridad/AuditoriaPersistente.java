package com.stayhub.seguridad;

import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;

/** Copia central consultable de la auditoría; el archivo local sigue siendo respaldo. */
@Stateless
public class AuditoriaPersistente {
    @PersistenceContext(unitName = "StayHubPU")
    EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void guardar(Instant fecha, String correlationId, String actorId, String rol,
                        String accion, String resultado, String recurso,
                        String origenHash, String detalle) {
        em.createNativeQuery("""
                INSERT INTO evento_auditoria_seguridad
                  (fecha, correlation_id, actor_id, rol, accion, resultado, recurso, origen_hash, detalle)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
                """)
                .setParameter(1, fecha).setParameter(2, correlationId)
                .setParameter(3, actorId).setParameter(4, rol)
                .setParameter(5, accion).setParameter(6, resultado)
                .setParameter(7, recurso).setParameter(8, origenHash)
                .setParameter(9, detalle).executeUpdate();
    }

    /** Retención acotada: 90 días de auditoría y ventanas ya vencidas. */
    @Schedule(hour = "3", minute = "17", second = "0", persistent = false)
    public void depurar() {
        em.createNativeQuery("DELETE FROM evento_auditoria_seguridad WHERE fecha < CURRENT_TIMESTAMP - INTERVAL '90 days'")
                .executeUpdate();
        em.createNativeQuery("DELETE FROM limite_solicitud WHERE expira_en < EXTRACT(EPOCH FROM clock_timestamp()) * 1000")
                .executeUpdate();
    }
}
