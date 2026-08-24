package com.stayhub.pagos.repository;

import com.stayhub.pagos.model.Pago;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class PagoRepositoryJpa implements PagoRepository {

    @PersistenceContext(unitName = "stayhubPU")
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
}