package com.stayhub.seguridad;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;

/** Copia central durable de la auditoría, compartida por todos los nodos. */
@Stateless
public class AuditoriaCentral {
    @PersistenceContext(unitName = "StayHubPU") EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void guardar(Instant fecha, String correlacion, String actor, String rol,
                        String accion, String resultado, String recurso,
                        String origenHash, String detalle) {
        em.createNativeQuery("""
                INSERT INTO seguridad_auditoria
                  (id, fecha, correlation_id, actor_id, rol, accion, resultado, recurso, origen_hash, detalle)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
                """).setParameter(1, UUID.randomUUID()).setParameter(2, fecha)
                .setParameter(3, correlacion).setParameter(4, actor).setParameter(5, rol)
                .setParameter(6, accion).setParameter(7, resultado).setParameter(8, recurso)
                .setParameter(9, origenHash).setParameter(10, detalle).executeUpdate();
    }
}
