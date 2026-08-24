package com.stayhub.hoteles.service;

import com.stayhub.hoteles.dto.*;
import com.stayhub.hoteles.model.*;
import java.util.List;

final class HotelMapper {
    private HotelMapper() { }
    static HotelResponse hotel(Hotel h, List<TipoHabitacion> tipos, List<Habitacion> habitaciones) {
        return new HotelResponse(h.getId(), h.getNombre(), h.getDireccion(), h.getCiudad(), h.getPais(),
                h.getDescripcion(), h.isActivo(), h.getServicios(), tipos.stream().map(HotelMapper::tipo).toList(),
                habitaciones.stream().map(HotelMapper::habitacion).toList());
    }
    static TipoHabitacionResponse tipo(TipoHabitacion t) {
        return new TipoHabitacionResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(),
                t.getCapacidadMaxima(), t.isActivo(), t.getCaracteristicas());
    }
    static HabitacionResponse habitacion(Habitacion h) {
        return new HabitacionResponse(h.getId(), h.getNumero(), h.getPiso(), h.isActiva(), h.getTipo().getId(),
                h.getTipo().getCodigo(), h.getTipo().getCapacidadMaxima(), h.getCaracteristicas());
    }
}
