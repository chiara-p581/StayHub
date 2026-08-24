package com.stayhub.hoteles.repository;

import com.stayhub.hoteles.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class HotelRepositoryJpa implements HotelRepository {
    @PersistenceContext(unitName = "stayhubPU")
    private EntityManager em;

    @Override public Hotel guardar(Hotel hotel) { return persistirOMezclar(hotel, hotel.getId()); }
    @Override public TipoHabitacion guardar(TipoHabitacion tipo) { return persistirOMezclar(tipo, tipo.getId()); }
    @Override public Habitacion guardar(Habitacion habitacion) { return persistirOMezclar(habitacion, habitacion.getId()); }

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
                TipoHabitacion.class, hotelId, codigo.toUpperCase());
    }

    @Override
    public Optional<Habitacion> buscarHabitacionPorNumero(Long hotelId, String numero) {
        return unico("SELECT h FROM Habitacion h WHERE h.hotel.id=:hotelId AND UPPER(h.numero)=:codigo",
                Habitacion.class, hotelId, numero.toUpperCase());
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
        return em.createQuery(jpql, Hotel.class).getResultList();
    }
    @Override public List<TipoHabitacion> listarTipos(Long hotelId) {
        return em.createQuery("SELECT t FROM TipoHabitacion t WHERE t.hotel.id=:id ORDER BY t.codigo", TipoHabitacion.class)
                .setParameter("id", hotelId).getResultList();
    }
    @Override public List<Habitacion> listarHabitaciones(Long hotelId) {
        return em.createQuery("SELECT h FROM Habitacion h WHERE h.hotel.id=:id ORDER BY h.numero", Habitacion.class)
                .setParameter("id", hotelId).getResultList();
    }
    @Override public long contarHabitacionesActivasPorTipo(Long tipoId) {
        return em.createQuery("SELECT COUNT(h) FROM Habitacion h WHERE h.tipo.id=:id AND h.activa=true", Long.class)
                .setParameter("id", tipoId).getSingleResult();
    }
}
