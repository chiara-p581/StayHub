package com.stayhub.integracionpms.api;

import com.stayhub.integracionpms.dto.DisponibilidadPmsDTO;
import com.stayhub.integracionpms.dto.ReservaPmsDTO;
import com.stayhub.integracionpms.dto.TarifaPmsDTO;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.reservas.dto.ReservaResponse;

import java.time.LocalDate;

/**
 * Traduce los DTOs internos (records Java) a los DTOs del contrato SOAP.
 * Hace falta porque JAXB (el que arma el XML en JAX-WS) no sabe serializar
 * records ni LocalDate, y porque así el WSDL no cambia si cambian los DTOs
 * internos de otro componente.
 */
final class IntegracionPmsMapper {

    private IntegracionPmsMapper() { }

    static ReservaPmsDTO aReserva(ReservaResponse r) {
        ReservaPmsDTO dto = new ReservaPmsDTO();
        dto.setId(r.id());
        dto.setCanal(r.canal());
        dto.setReferenciaExterna(r.referenciaExterna());
        dto.setHotelId(r.hotelId());
        dto.setTipoHabitacion(r.tipoHabitacion());
        dto.setCantidadHabitaciones(r.cantidadHabitaciones());
        dto.setCheckIn(texto(r.checkIn()));
        dto.setCheckOut(texto(r.checkOut()));
        dto.setHuespedNombre(r.huespedNombre());
        dto.setHuespedApellido(r.huespedApellido());
        dto.setHuespedEmail(r.huespedEmail());
        dto.setPrecioTotal(r.precioTotal());
        dto.setMoneda(r.moneda());
        dto.setEstado(r.estado());
        return dto;
    }

    static DisponibilidadPmsDTO aDisponibilidad(DisponibilidadDTO d) {
        DisponibilidadPmsDTO dto = new DisponibilidadPmsDTO();
        dto.setHotelId(d.hotelId());
        dto.setTipoHabitacion(d.tipoHabitacion());
        dto.setDesde(texto(d.desde()));
        dto.setHasta(texto(d.hasta()));
        dto.setUnidadesDisponibles(d.unidadesDisponibles());
        return dto;
    }

    static TarifaPmsDTO aTarifa(TarifaDTO t) {
        TarifaPmsDTO dto = new TarifaPmsDTO();
        dto.setHotelId(t.hotelId());
        dto.setTipoHabitacion(t.tipoHabitacion());
        dto.setDesde(texto(t.desde()));
        dto.setHasta(texto(t.hasta()));
        dto.setImporte(t.importe());
        dto.setMoneda(t.moneda());
        return dto;
    }

    private static String texto(LocalDate fecha) {
        return fecha == null ? null : fecha.toString();
    }
}
