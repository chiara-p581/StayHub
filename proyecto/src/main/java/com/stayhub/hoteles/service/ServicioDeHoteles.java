package com.stayhub.hoteles.service;

import com.stayhub.hoteles.dto.*;
import java.util.List;

/**
 * Facade pública del componente ServicioDeHoteles.
 *
 * <p>Concentra los casos de uso administrativos y de consulta para que la capa REST no dependa
 * de entidades JPA ni de detalles de persistencia. Las operaciones reciben y devuelven DTOs.</p>
 */
public interface ServicioDeHoteles {
    HotelResponse crearHotel(HotelRequest solicitud);
    HotelResponse modificarHotel(Long id, HotelRequest solicitud);
    HotelResponse consultarHotel(Long id);
    List<HotelResponse> listarHoteles(boolean incluirInactivos);
    List<HotelResponse> listarHoteles(boolean incluirInactivos, int offset, int limite);
    HotelResponse darDeBajaHotel(Long id);

    TipoHabitacionResponse crearTipo(Long hotelId, TipoHabitacionRequest solicitud);
    TipoHabitacionResponse modificarTipo(Long hotelId, Long tipoId, TipoHabitacionRequest solicitud);
    TipoHabitacionResponse consultarTipo(Long hotelId, Long tipoId);
    List<TipoHabitacionResponse> listarTipos(Long hotelId, boolean incluirInactivos);
    List<TipoHabitacionResponse> listarTipos(Long hotelId, boolean incluir, int offset, int limite);
    void darDeBajaTipo(Long hotelId, Long tipoId);

    HabitacionResponse crearHabitacion(Long hotelId, HabitacionRequest solicitud);
    HabitacionResponse modificarHabitacion(Long hotelId, Long habitacionId, HabitacionRequest solicitud);
    HabitacionResponse consultarHabitacion(Long hotelId, Long habitacionId);
    List<HabitacionResponse> listarHabitaciones(Long hotelId, boolean incluirInactivas);
    List<HabitacionResponse> listarHabitaciones(Long hotelId, boolean incluir, int offset, int limite);
    void darDeBajaHabitacion(Long hotelId, Long habitacionId);
}
