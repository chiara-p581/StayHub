package com.stayhub.hoteles.contrato;

import com.stayhub.hoteles.dto.HotelResponse;

/**
 * Puerto de lectura que ServicioDeHoteles expone a otros componentes de StayHub.
 *
 * <p>Reservas e Inventario pueden validar la estructura hotelera sin acceder al repositorio ni
 * depender de los endpoints REST del componente.</p>
 */
public interface ServicioDeHotelesPort {
    /** Devuelve la información descriptiva completa de un hotel. */
    HotelResponse consultarHotel(Long hotelId);

    /** Indica si el hotel existe y está activo. */
    boolean existeHotelActivo(Long hotelId);

    /** Indica si el código identifica un tipo activo perteneciente a un hotel activo. */
    boolean existeTipoHabitacionActivo(Long hotelId, String codigo);

    /** Indica si la habitación pertenece al hotel y ambos están activos. */
    boolean existeHabitacionActiva(Long hotelId, Long habitacionId);

    /** Devuelve la capacidad máxima de un tipo activo dentro del hotel. */
    int consultarCapacidadTipo(Long hotelId, String codigo);
}
