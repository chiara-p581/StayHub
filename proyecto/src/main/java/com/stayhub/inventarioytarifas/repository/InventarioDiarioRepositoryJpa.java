package com.stayhub.inventarioytarifas.repository;

import com.stayhub.inventarioytarifas.model.InventarioDiario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class InventarioDiarioRepositoryJpa implements InventarioDiarioRepository {

    private static final String POR_HOTEL_TIPO_Y_FECHA =
            "SELECT i FROM InventarioDiario i " +
            "WHERE i.hotelId = :hotelId AND i.tipoHabitacion = :tipo AND i.fecha = :fecha";

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
        return unicoResultado(consultaPorHotelTipoYFecha(hotelId, tipoHabitacion, fecha));
    }

    @Override
    public Optional<InventarioDiario> buscarPorHotelTipoYFechaBloqueando(Long hotelId, String tipoHabitacion,
                                                                         LocalDate fecha) {
        return unicoResultado(consultaPorHotelTipoYFecha(hotelId, tipoHabitacion, fecha)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE));
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

    private TypedQuery<InventarioDiario> consultaPorHotelTipoYFecha(Long hotelId, String tipoHabitacion,
                                                                    LocalDate fecha) {
        return em.createQuery(POR_HOTEL_TIPO_Y_FECHA, InventarioDiario.class)
                .setParameter("hotelId", hotelId)
                .setParameter("tipo", tipoHabitacion)
                .setParameter("fecha", fecha);
    }

    private Optional<InventarioDiario> unicoResultado(TypedQuery<InventarioDiario> consulta) {
        try {
            return Optional.of(consulta.getSingleResult());
        } catch (NoResultException ex) {
            return Optional.empty();
        }
    }
}
