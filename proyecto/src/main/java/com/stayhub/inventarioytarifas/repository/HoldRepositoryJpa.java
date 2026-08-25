package com.stayhub.inventarioytarifas.repository;

import com.stayhub.inventarioytarifas.model.Hold;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class HoldRepositoryJpa implements HoldRepository {

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Override
    public Hold guardar(Hold hold) {
        // Hold usa clave natural (UUID) asignada antes de persistir, no autogenerada:
        // a diferencia de los repositorios con id Long, acá no hay forma de distinguir
        // alta de actualización mirando el id, así que merge sirve para ambos casos.
        return em.merge(hold);
    }

    @Override
    public Optional<Hold> buscarPorId(String id) {
        return Optional.ofNullable(em.find(Hold.class, id));
    }
}
