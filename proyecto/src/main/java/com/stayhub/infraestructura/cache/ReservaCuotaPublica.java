package com.stayhub.infraestructura.cache;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Adaptador de lecturas públicas sobre la misma tabla persistente de development. */
@Stateless
public class ReservaCuotaPublica {
    @PersistenceContext(unitName = "StayHubPU") private EntityManager em;

    public record Bloque(int permisos, long expiraEn) { }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Bloque reservar(String clave, int limite, int bloque, long ahora) {
        Object[] fila = (Object[]) em.createNativeQuery("""
                INSERT INTO limite_solicitud (clave, ventana_inicio, cantidad, expira_en)
                VALUES (?1, ?2, ?3, ?4)
                ON CONFLICT (clave) DO UPDATE SET
                  ventana_inicio = CASE WHEN limite_solicitud.expira_en <= ?2 THEN ?2 ELSE limite_solicitud.ventana_inicio END,
                  cantidad = CASE WHEN limite_solicitud.expira_en <= ?2 THEN ?3 ELSE limite_solicitud.cantidad + ?3 END,
                  expira_en = CASE WHEN limite_solicitud.expira_en <= ?2 THEN ?4 ELSE limite_solicitud.expira_en END
                RETURNING cantidad, expira_en
                """).setParameter(1, clave).setParameter(2, ahora)
                .setParameter(3, bloque).setParameter(4, ahora + 60_000).getSingleResult();
        long total = ((Number) fila[0]).longValue();
        int disponibles = (int) Math.max(0, Math.min(bloque, limite - (total - bloque)));
        return new Bloque(disponibles, ((Number) fila[1]).longValue());
    }
}
