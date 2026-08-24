package com.stayhub.hoteles.contrato;

import com.stayhub.hoteles.dto.HotelResponse;

/** Contrato de lectura para Reservas, Inventario y otros componentes internos. */
public interface ServicioDeHotelesPort {
    HotelResponse consultarHotel(Long hotelId);
    boolean existeHotelActivo(Long hotelId);
    boolean existeTipoHabitacionActivo(Long hotelId, String codigo);
    boolean existeHabitacionActiva(Long hotelId, Long habitacionId);
    int consultarCapacidadTipo(Long hotelId, String codigo);
}
