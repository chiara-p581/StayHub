package com.stayhub.inventarioytarifas.repository;

import com.stayhub.inventarioytarifas.model.InventarioDiario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class InventarioDiarioRepositoryJpa implements InventarioDiarioRepository {

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Override
    public InventarioDiario guardar(InventarioDiario inventario) {
        if (inventario.getId() == null) {
            em.persist(inventario);
            return inventario;
        }
        return em.merge(inventario);
    }

    @Override
    public Optional<InventarioDiario> buscarPorHotelTipoYFecha(Long hotelId, String tipoHabitacion, LocalDate fecha) {
        try {
            InventarioDiario inventario = em.createQuery(
                    "SELECT i FROM InventarioDiario i " +
                    "WHERE i.hotelId = :hotelId AND i.tipoHabitacion = :tipo AND i.fecha = :fecha",
                    InventarioDiario.class)
                .setParameter("hotelId", hotelId)
                .setParameter("tipo", tipoHabitacion)
                .setParameter("fecha", fecha)
                .getSingleResult();
            return Optional.of(inventario);
        } catch (NoResultException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<InventarioDiario> buscarPorHotelYRango(Long hotelId, LocalDate desde, LocalDate hasta) {
        return em.createQuery(
                "SELECT i FROM InventarioDiario i " +
                "WHERE i.hotelId = :hotelId AND i.fecha >= :desde AND i.fecha < :hasta",
                InventarioDiario.class)
            .setParameter("hotelId", hotelId)
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .getResultList();
    }
}
