package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Lista de JWT revocados guardada en PostgreSQL (tabla {@code token_revocado}).
 *
 * <p>Así una revocación (logout, rotación o cambio de MFA) sigue valiendo aunque se
 * reinicie WildFly o haya más de un nodo. Cada fila vive solo hasta que el token
 * vencería solo, por eso la tabla queda chica.</p>
 */
@ApplicationScoped
public class RevocacionTokens {
    @PersistenceContext(unitName = "StayHubPU")
    EntityManager em;

    /** Guarda que el token {@code jti} deja de valer a partir de {@code revocadoDesde}. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void guardar(String jti, long revocadoDesde, long expiraEn, long ahora) {
        em.createNativeQuery("DELETE FROM token_revocado WHERE expira_en <= ?1")
                .setParameter(1, ahora).executeUpdate();
        em.createNativeQuery("""
                INSERT INTO token_revocado (jti, revocado_desde, expira_en)
                VALUES (?1, ?2, ?3)
                ON CONFLICT (jti) DO UPDATE SET
                  revocado_desde = LEAST(token_revocado.revocado_desde, EXCLUDED.revocado_desde)
                """)
                .setParameter(1, jti).setParameter(2, revocadoDesde).setParameter(3, expiraEn)
                .executeUpdate();
    }

    /** Desde cuándo está revocado el token, o {@code null} si no lo está. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Long revocadoDesde(String jti) {
        Object valor = em.createNativeQuery("SELECT revocado_desde FROM token_revocado WHERE jti = ?1")
                .setParameter(1, jti).getResultStream().findFirst().orElse(null);
        return valor instanceof Number n ? n.longValue() : null;
    }
}
