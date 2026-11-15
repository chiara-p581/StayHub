package com.stayhub.pagos.repository;

import com.stayhub.pagos.model.Pago;
import com.stayhub.pagos.model.EstadoPago;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

@ApplicationScoped
public class PagoRepositoryJpa implements PagoRepository {

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Override
    public Pago guardar(Pago pago) {
        if (pago.getId() == null) {
            em.persist(pago);
            return pago;
        }
        return em.merge(pago);
    }

    @Override
    public Optional<Pago> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Pago.class, id));
    }

    @Override
    public Optional<Pago> buscarPorClaveIdempotencia(Long usuarioId, String clave) {
        if (usuarioId == null || clave == null || clave.isBlank()) return Optional.empty();
        return em.createQuery("SELECT p FROM Pago p WHERE p.usuarioId=:usuarioId AND p.claveIdempotencia=:clave", Pago.class)
                .setParameter("usuarioId", usuarioId).setParameter("clave", clave).getResultStream().findFirst();
    }

    @Override
    public void bloquearUsuario(Long usuarioId) {
        em.find(com.stayhub.usuarios.model.Usuario.class, usuarioId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public void bloquearReservas(List<Long> reservaIds) {
        reservaIds.stream().sorted().forEach(id ->
                em.createNativeQuery("SELECT pg_advisory_xact_lock(?1)").setParameter(1, id).getSingleResult());
    }

    @Override
    public boolean existeAprobadoParaReserva(Long reservaId) {
        Long cantidad = em.createQuery("SELECT COUNT(p) FROM Pago p WHERE p.estado=:estado " +
                        "AND (p.reservaId=:reservaId OR :reservaId MEMBER OF p.reservaIds)", Long.class)
                .setParameter("estado", EstadoPago.APROBADO)
                .setParameter("reservaId", reservaId).getSingleResult();
        return cantidad > 0;
    }

    @Override
    public List<Pago> listarPorReservas(List<Long> reservaIds) {
        if (reservaIds == null || reservaIds.isEmpty()) return List.of();
        return em.createQuery("SELECT DISTINCT p FROM Pago p LEFT JOIN p.reservaIds r "
                        + "WHERE p.reservaId IN :ids OR r IN :ids ORDER BY p.fechaPago DESC", Pago.class)
                .setParameter("ids", reservaIds)
                .setMaxResults(100).getResultList();
    }

    @Override public List<Pago> listarPorUsuario(Long id, int offset, int limite) {
        com.stayhub.seguridad.Limites.pagina(offset, limite);
        return em.createQuery("SELECT p FROM Pago p WHERE EXISTS (SELECT r.id FROM Reserva r WHERE r.usuarioId=:id AND (r.id=p.reservaId OR r.id MEMBER OF p.reservaIds)) ORDER BY p.id DESC", Pago.class)
                .setParameter("id",id).setFirstResult(offset).setMaxResults(limite).getResultList();
    }

    @Override
    public List<Pago> buscarAdmin(Long id, String usuario, Long hotelId, BigDecimal montoMin,
            BigDecimal montoMax, String estado, int offset, int limite) {
        var q = consultaAdmin(false, id, usuario, hotelId, montoMin, montoMax, estado);
        @SuppressWarnings("unchecked")
        List<Pago> resultado = (List<Pago>) (List<?>) q.setFirstResult(offset).setMaxResults(limite).getResultList();
        return resultado;
    }

    @Override
    public long contarAdmin(Long id, String usuario, Long hotelId, BigDecimal montoMin,
            BigDecimal montoMax, String estado) {
        return (Long) consultaAdmin(true, id, usuario, hotelId, montoMin, montoMax, estado).getSingleResult();
    }

    private jakarta.persistence.TypedQuery<?> consultaAdmin(boolean contar, Long id, String usuario, Long hotelId,
            BigDecimal montoMin, BigDecimal montoMax, String estado) {
        StringBuilder jpql = new StringBuilder(contar ? "SELECT COUNT(DISTINCT p) FROM Pago p" : "SELECT DISTINCT p FROM Pago p");
        jpql.append(" WHERE 1=1");
        if (id != null) jpql.append(" AND p.id=:id");
        if (montoMin != null) jpql.append(" AND p.monto>=:montoMin");
        if (montoMax != null) jpql.append(" AND p.monto<=:montoMax");
        if (estado != null && !estado.isBlank()) jpql.append(" AND p.estado=:estado");
        if (usuario != null && !usuario.isBlank()) jpql.append(" AND EXISTS (SELECT u.id FROM Usuario u WHERE u.id=p.usuarioId AND (lower(u.email) LIKE :usuario OR lower(u.nombre) LIKE :usuario OR lower(u.apellido) LIKE :usuario))");
        if (hotelId != null) jpql.append(" AND EXISTS (SELECT r.id FROM Reserva r WHERE r.hotelId=:hotelId AND (r.id=p.reservaId OR r.id MEMBER OF p.reservaIds))");
        if (!contar) jpql.append(" ORDER BY p.fechaPago DESC, p.id DESC");
        jakarta.persistence.TypedQuery<?> q = contar ? em.createQuery(jpql.toString(), Long.class) : em.createQuery(jpql.toString(), Pago.class);
        if (id != null) q.setParameter("id", id);
        if (montoMin != null) q.setParameter("montoMin", montoMin);
        if (montoMax != null) q.setParameter("montoMax", montoMax);
        if (estado != null && !estado.isBlank()) q.setParameter("estado", EstadoPago.valueOf(estado.toUpperCase()));
        if (usuario != null && !usuario.isBlank()) q.setParameter("usuario", "%" + usuario.trim().toLowerCase() + "%");
        if (hotelId != null) q.setParameter("hotelId", hotelId);
        return q;
    }
}
