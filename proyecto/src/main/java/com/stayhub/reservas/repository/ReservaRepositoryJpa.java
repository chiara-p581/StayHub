package com.stayhub.reservas.repository;

import com.stayhub.reservas.model.Reserva;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA del DAO. @ApplicationScoped (bean CDI) para que se
 * pueda inyectar con @Inject en el EJB de servicio.
 *
 * La unidad de persistencia "StayHubPU" es la que quedó definida en
 * proyecto/src/main/resources/META-INF/persistence.xml (compartida por
 * todos los componentes, ya que es un único WAR sobre PostgreSQL).
 */
@ApplicationScoped
public class ReservaRepositoryJpa implements ReservaRepository {

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Override
    public Reserva guardar(Reserva reserva) {
        if (reserva.getId() == null) {
            em.persist(reserva);
            return reserva;
        }
        return em.merge(reserva);
    }

    @Override
    public Optional<Reserva> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Reserva.class, id));
    }

    @Override
    public Optional<Reserva> buscarPorCanalYReferencia(String canal, String referenciaExterna) {
        try {
            Reserva reserva = em.createQuery(
                    "SELECT r FROM Reserva r WHERE r.canal = :canal AND r.referenciaExterna = :ref",
                    Reserva.class)
                .setParameter("canal", canal)
                .setParameter("ref", referenciaExterna)
                .getSingleResult();
            return Optional.of(reserva);
        } catch (NoResultException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<Reserva> listarPorHotel(Long hotelId) {
        return em.createQuery("SELECT r FROM Reserva r WHERE r.hotelId = :hotelId", Reserva.class)
                .setParameter("hotelId", hotelId)
                .getResultList();
    }
}