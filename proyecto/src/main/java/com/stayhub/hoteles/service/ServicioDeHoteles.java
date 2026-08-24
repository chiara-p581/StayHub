package com.stayhub.hoteles.service;

import com.stayhub.hoteles.dto.*;
import java.util.List;

public interface ServicioDeHoteles {
    HotelResponse crearHotel(HotelRequest solicitud);
    HotelResponse modificarHotel(Long id, HotelRequest solicitud);
    HotelResponse consultarHotel(Long id);
    List<HotelResponse> listarHoteles(boolean incluirInactivos);
    HotelResponse darDeBajaHotel(Long id);
    TipoHabitacionResponse crearTipo(Long hotelId, TipoHabitacionRequest solicitud);
    TipoHabitacionResponse modificarTipo(Long hotelId, Long tipoId, TipoHabitacionRequest solicitud);
    void darDeBajaTipo(Long hotelId, Long tipoId);
    HabitacionResponse crearHabitacion(Long hotelId, HabitacionRequest solicitud);
    HabitacionResponse modificarHabitacion(Long hotelId, Long habitacionId, HabitacionRequest solicitud);
    void darDeBajaHabitacion(Long hotelId, Long habitacionId);
}
