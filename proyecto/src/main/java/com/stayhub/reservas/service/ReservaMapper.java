package com.stayhub.reservas.service;

import com.stayhub.canalesexternos.contrato.interno.ResultadoOperacionReserva;
import com.stayhub.canalesexternos.contrato.interno.SolicitudReserva;
import com.stayhub.canalesexternos.dto.HuespedDTO;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.model.Huesped;
import com.stayhub.reservas.model.Reserva;

/**
 * Traduce entre los tipos "propios" de ServicioDeReservas y los tipos del
 * contrato definido en com.stayhub.canalesexternos.contrato.interno. Mantener
 * esta conversión en un solo lugar evita esparcir el acoplamiento con el
 * paquete de otro componente por toda la clase de servicio.
 */
final class ReservaMapper {

    private ReservaMapper() { }

    static Huesped huespedDesde(HuespedDTO dto) {
        return new Huesped(dto.nombre(), dto.apellido(), dto.email(), dto.telefono());
    }

    static Huesped huespedDesde(ReservaRequest r) {
        return new Huesped(r.huespedNombre(), r.huespedApellido(), r.huespedEmail(), r.huespedTelefono());
    }

    static Reserva nuevaDesdeCanal(SolicitudReserva s) {
        return new Reserva(s.canal(), s.referenciaExterna(), s.hotelId(), s.tipoHabitacion(),
                s.cantidadHabitaciones(), s.checkIn(), s.checkOut(), huespedDesde(s.huesped()),
                s.precioTotal(), s.moneda());
    }

    static Reserva nuevaDirecta(ReservaRequest r) {
        return new Reserva("DIRECTA", null, r.hotelId(), r.tipoHabitacion(), r.cantidadHabitaciones(),
                r.checkIn(), r.checkOut(), huespedDesde(r), r.precioTotal(), r.moneda());
    }

    static ResultadoOperacionReserva aResultadoOperacion(Reserva r) {
        return new ResultadoOperacionReserva(String.valueOf(r.getId()), r.getEstado().name());
    }

    static ReservaResponse aResponse(Reserva r) {
        return new ReservaResponse(r.getId(), r.getCanal(), r.getReferenciaExterna(), r.getHotelId(),
                r.getTipoHabitacion(), r.getCantidadHabitaciones(), r.getCheckIn(), r.getCheckOut(),
                r.getHuesped().getNombre(), r.getHuesped().getApellido(), r.getHuesped().getEmail(),
                r.getPrecioTotal(), r.getMoneda(), r.getEstado().name(), r.getFechaCreacion());
    }
}
