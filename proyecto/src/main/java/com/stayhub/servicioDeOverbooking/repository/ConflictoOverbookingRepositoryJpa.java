package com.stayhub.servicioDeOverbooking.repository;

import com.stayhub.servicioDeOverbooking.model.ConflictoOverbooking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class ConflictoOverbookingRepositoryJpa implements ConflictoOverbookingRepository {

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Override
    public ConflictoOverbooking guardar(ConflictoOverbooking conflicto) {
        if (conflicto.getId() == null) {
            em.persist(conflicto);
            return conflicto;
        }
        return em.merge(conflicto);
    }

    @Override
    public Optional<ConflictoOverbooking> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(ConflictoOverbooking.class, id));
    }
}