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

    @Override public Optional<Reserva> buscarPorIdBloqueando(Long id) {
        Reserva reserva = em.find(Reserva.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (reserva != null) em.refresh(reserva, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(reserva);
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
                .setMaxResults(com.stayhub.seguridad.Limites.RESULTADOS).getResultList();
    }

    @Override
    public List<Reserva> listarPorHuespedEmail(String email) {
        return em.createQuery("SELECT r FROM Reserva r WHERE lower(r.huesped.email) = lower(:email) "
                        + "ORDER BY r.checkIn DESC", Reserva.class)
                .setParameter("email", email)
                .setMaxResults(com.stayhub.seguridad.Limites.RESULTADOS).getResultList();
    }

    @Override public List<Reserva> listarPorUsuario(Long id, int offset, int limite) {
        com.stayhub.seguridad.Limites.pagina(offset, limite);
        return em.createQuery("SELECT r FROM Reserva r WHERE r.usuarioId=:id ORDER BY r.id DESC", Reserva.class)
                .setParameter("id", id).setFirstResult(offset).setMaxResults(limite).getResultList();
    }
    @Override public List<Reserva> listarPorHotel(Long hotelId, int offset, int limite) {
        com.stayhub.seguridad.Limites.pagina(offset, limite);
        return em.createQuery("SELECT r FROM Reserva r WHERE r.hotelId=:id ORDER BY r.id DESC", Reserva.class)
                .setParameter("id", hotelId).setFirstResult(offset).setMaxResults(limite).getResultList();
    }
    @Override public long contarPendientesPorUsuario(Long id) {
        return em.createQuery("SELECT COUNT(r) FROM Reserva r WHERE r.usuarioId=:id AND r.estado=:estado", Long.class)
                .setParameter("id", id).setParameter("estado", com.stayhub.reservas.model.EstadoReserva.PENDIENTE).getSingleResult();
    }
    @Override public void bloquearUsuario(Long id) {
        em.find(com.stayhub.usuarios.model.Usuario.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
    @Override public List<Reserva> pendientesVencidas(java.time.LocalDateTime antes) {
        return em.createQuery("SELECT r FROM Reserva r WHERE r.estado=:estado AND r.usuarioId IS NOT NULL AND r.fechaCreacion < :antes ORDER BY r.id", Reserva.class)
                .setParameter("estado", com.stayhub.reservas.model.EstadoReserva.PENDIENTE).setParameter("antes", antes)
                .setMaxResults(100).getResultList();
    }
    @Override public List<Reserva> listarLlegadas(Long hotelId, java.time.LocalDate fecha) {
        return em.createQuery("SELECT r FROM Reserva r WHERE r.hotelId=:hotel AND r.checkIn=:fecha AND r.estado=:estado ORDER BY r.id", Reserva.class)
                .setParameter("hotel", hotelId).setParameter("fecha", fecha).setParameter("estado", com.stayhub.reservas.model.EstadoReserva.CONFIRMADA)
                .setMaxResults(100).getResultList();
    }
}
