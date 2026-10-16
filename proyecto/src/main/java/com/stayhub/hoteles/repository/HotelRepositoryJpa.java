package com.stayhub.hoteles.repository;

import com.stayhub.hoteles.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Implementación del patrón DAO/Repository mediante Jakarta Persistence. */
@ApplicationScoped
public class HotelRepositoryJpa implements HotelRepository {
    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Override public Hotel guardar(Hotel hotel) { return persistirOMezclar(hotel, hotel.getId()); }
    @Override public TipoHabitacion guardar(TipoHabitacion tipo) { return persistirOMezclar(tipo, tipo.getId()); }
    @Override public Habitacion guardar(Habitacion habitacion) { return persistirOMezclar(habitacion, habitacion.getId()); }
    @Override public void sincronizar() { em.flush(); }

    private <T> T persistirOMezclar(T entidad, Long id) {
        if (id == null) { em.persist(entidad); return entidad; }
        return em.merge(entidad);
    }

    @Override public Optional<Hotel> buscarHotel(Long id) { return Optional.ofNullable(em.find(Hotel.class, id)); }
    @Override public Optional<TipoHabitacion> buscarTipo(Long id) {
        return Optional.ofNullable(em.find(TipoHabitacion.class, id));
    }
    @Override public Optional<Habitacion> buscarHabitacion(Long id) {
        return Optional.ofNullable(em.find(Habitacion.class, id));
    }

    @Override
    public Optional<TipoHabitacion> buscarTipoPorCodigo(Long hotelId, String codigo) {
        return unico("SELECT t FROM TipoHabitacion t WHERE t.hotel.id=:hotelId AND UPPER(t.codigo)=:codigo",
                TipoHabitacion.class, hotelId, codigo.toUpperCase(Locale.ROOT));
    }

    @Override
    public Optional<Habitacion> buscarHabitacionPorNumero(Long hotelId, String numero) {
        return unico("SELECT h FROM Habitacion h WHERE h.hotel.id=:hotelId AND UPPER(h.numero)=:codigo",
                Habitacion.class, hotelId, numero.toUpperCase(Locale.ROOT));
    }

    private <T> Optional<T> unico(String jpql, Class<T> tipo, Long hotelId, String codigo) {
        try {
            return Optional.of(em.createQuery(jpql, tipo).setParameter("hotelId", hotelId)
                    .setParameter("codigo", codigo).getSingleResult());
        } catch (NoResultException ex) { return Optional.empty(); }
    }

    @Override
    public List<Hotel> listarHoteles(boolean incluirInactivos) {
        String jpql = "SELECT h FROM Hotel h" + (incluirInactivos ? "" : " WHERE h.activo=true") + " ORDER BY h.nombre";
        return em.createQuery(jpql, Hotel.class).setMaxResults(100).getResultList();
    }
    @Override public List<TipoHabitacion> listarTipos(Long hotelId) {
        return em.createQuery("SELECT t FROM TipoHabitacion t WHERE t.hotel.id=:id ORDER BY t.codigo", TipoHabitacion.class)
                .setParameter("id", hotelId).setMaxResults(100).getResultList();
    }
    @Override public List<Habitacion> listarHabitaciones(Long hotelId) {
        return em.createQuery("SELECT h FROM Habitacion h WHERE h.hotel.id=:id ORDER BY h.numero", Habitacion.class)
                .setParameter("id", hotelId).setMaxResults(100).getResultList();
    }
    @Override public long contarHabitacionesActivasPorTipo(Long tipoId) {
        return em.createQuery("SELECT COUNT(h) FROM Habitacion h WHERE h.tipo.id=:id AND h.activa=true", Long.class)
                .setParameter("id", tipoId).getSingleResult();
    }

    @Override public List<Hotel> listarHoteles(boolean incluir, int offset, int limite) {
        com.stayhub.seguridad.Limites.pagina(offset, limite);
        return em.createQuery("SELECT h FROM Hotel h" + (incluir ? "" : " WHERE h.activo=true") + " ORDER BY h.id", Hotel.class)
                .setFirstResult(offset).setMaxResults(limite).getResultList();
    }
    @Override public void darDeBajaDependientes(Long hotelId) {
        em.createQuery("UPDATE Habitacion h SET h.activa=false WHERE h.hotel.id=:id").setParameter("id",hotelId).executeUpdate();
        em.createQuery("UPDATE TipoHabitacion t SET t.activo=false WHERE t.hotel.id=:id").setParameter("id",hotelId).executeUpdate();
    }

    @Override public List<TipoHabitacion> listarTipos(Long id, boolean incluir, int offset, int limite) {
        com.stayhub.seguridad.Limites.pagina(offset,limite);
        return em.createQuery("SELECT t FROM TipoHabitacion t WHERE t.hotel.id=:id" + (incluir ? "" : " AND t.activo=true") + " ORDER BY t.id", TipoHabitacion.class)
                .setParameter("id",id).setFirstResult(offset).setMaxResults(limite).getResultList();
    }
    @Override public List<Habitacion> listarHabitaciones(Long id, boolean incluir, int offset, int limite) {
        com.stayhub.seguridad.Limites.pagina(offset,limite);
        return em.createQuery("SELECT h FROM Habitacion h WHERE h.hotel.id=:id" + (incluir ? "" : " AND h.activa=true") + " ORDER BY h.id", Habitacion.class)
                .setParameter("id",id).setFirstResult(offset).setMaxResults(limite).getResultList();
    }
}
