package com.stayhub.hoteles.repository;

import com.stayhub.hoteles.model.*;
import java.util.List;
import java.util.Optional;

/**
 * Abstracción DAO/Repository de la capa de datos de ServicioDeHoteles.
 *
 * <p>La capa de negocio depende de este contrato y no de JPA, Hibernate o PostgreSQL.</p>
 */
public interface HotelRepository {
    Hotel guardar(Hotel hotel);
    TipoHabitacion guardar(TipoHabitacion tipo);
    Habitacion guardar(Habitacion habitacion);
    Optional<Hotel> buscarHotel(Long id);
    Optional<TipoHabitacion> buscarTipo(Long id);
    Optional<TipoHabitacion> buscarTipoPorCodigo(Long hotelId, String codigo);
    Optional<Habitacion> buscarHabitacion(Long id);
    Optional<Habitacion> buscarHabitacionPorNumero(Long hotelId, String numero);
    List<Hotel> listarHoteles(boolean incluirInactivos);
    List<TipoHabitacion> listarTipos(Long hotelId);
    List<Habitacion> listarHabitaciones(Long hotelId);
    long contarHabitacionesActivasPorTipo(Long tipoId);
}
