package com.stayhub.pagos.repository;

import com.stayhub.pagos.model.Pago;
import com.stayhub.pagos.model.EstadoPago;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

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
    public boolean existeAprobadoParaReserva(Long reservaId) {
        Long cantidad = em.createQuery("SELECT COUNT(p) FROM Pago p WHERE p.estado=:estado " +
                        "AND (p.reservaId=:reservaId OR :reservaId MEMBER OF p.reservaIds)", Long.class)
                .setParameter("estado", EstadoPago.APROBADO)
                .setParameter("reservaId", reservaId).getSingleResult();
        return cantidad > 0;
    }
}
