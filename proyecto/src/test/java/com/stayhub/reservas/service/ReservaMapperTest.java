package com.stayhub.reservas.service;

import static org.junit.jupiter.api.Assertions.*;

import com.stayhub.canalesexternos.contrato.interno.SolicitudReserva;
import com.stayhub.canalesexternos.dto.HuespedDTO;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.model.EstadoReserva;
import com.stayhub.reservas.model.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReservaMapperTest {
    LocalDate desde = LocalDate.now().plusDays(2), hasta = desde.plusDays(3);

    private Reserva directa() {
        return ReservaMapper.nuevaDirecta(new ReservaRequest(6L, "T", 1, desde, hasta, "A", "B", "a@b.co", "1", BigDecimal.ONE, "ARS"));
    }

    private void asignarId(Reserva r, long id) throws Exception {
        var f = Reserva.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, id);
    }

    @Test void huespedDesdeDtoCopiaLosCuatroCampos() {
        var h = ReservaMapper.huespedDesde(new HuespedDTO("Luz", "Gil", "luz@mail.com", "111"));
        assertEquals("Luz", h.getNombre());
        assertEquals("Gil", h.getApellido());
        assertEquals("luz@mail.com", h.getEmail());
        assertEquals("111", h.getTelefono());
    }

    @Test void huespedDesdeRequestCopiaLosCuatroCampos() {
        var h = ReservaMapper.huespedDesde(new ReservaRequest(1L, "DOBLE", 1, desde, hasta, "Ana", "Paz", "a@b.co", "222", null, null));
        assertEquals("Ana", h.getNombre());
        assertEquals("Paz", h.getApellido());
        assertEquals("a@b.co", h.getEmail());
        assertEquals("222", h.getTelefono());
    }

    @Test void nuevaDesdeCanalConservaCanalYReferenciaYQuedaPendiente() {
        var s = new SolicitudReserva("EXT-1", "EXPEDIA", 5L, desde, hasta, "SUITE", 2,
                new HuespedDTO("Luz", "Gil", "luz@mail.com", null), new BigDecimal("10.50"), "EUR");
        Reserva r = ReservaMapper.nuevaDesdeCanal(s);
        assertEquals("EXPEDIA", r.getCanal());
        assertEquals("EXT-1", r.getReferenciaExterna());
        assertEquals(5L, r.getHotelId());
        assertEquals("SUITE", r.getTipoHabitacion());
        assertEquals(2, r.getCantidadHabitaciones());
        assertEquals(desde, r.getCheckIn());
        assertEquals(hasta, r.getCheckOut());
        assertEquals("Luz", r.getHuesped().getNombre());
        assertNull(r.getHuesped().getTelefono());
        assertEquals(new BigDecimal("10.50"), r.getPrecioTotal());
        assertEquals("EUR", r.getMoneda());
        assertEquals(EstadoReserva.PENDIENTE, r.getEstado());
    }

    @Test void nuevaDirectaUsaCanalDirectaSinReferenciaExterna() {
        var q = new ReservaRequest(6L, "TRIPLE", 3, desde, hasta, "Ana", "Paz", "a@b.co", "222", new BigDecimal("30"), "ARS");
        Reserva r = ReservaMapper.nuevaDirecta(q);
        assertEquals("DIRECTA", r.getCanal());
        assertNull(r.getReferenciaExterna());
        assertEquals(6L, r.getHotelId());
        assertEquals("TRIPLE", r.getTipoHabitacion());
        assertEquals(3, r.getCantidadHabitaciones());
        assertEquals(new BigDecimal("30"), r.getPrecioTotal());
        assertEquals("ARS", r.getMoneda());
        assertEquals("a@b.co", r.getHuesped().getEmail());
        assertEquals(EstadoReserva.PENDIENTE, r.getEstado());
    }

    @Test void aResultadoOperacionExponeIdTextualYEstado() throws Exception {
        Reserva r = directa();
        asignarId(r, 42L);
        r.cancelar();
        var res = ReservaMapper.aResultadoOperacion(r);
        assertEquals("42", res.reservaId());
        assertEquals("CANCELADA", res.estado());
    }

    @Test void aResultadoOperacionConIdNuloDevuelveTextoNull() {
        assertEquals("null", ReservaMapper.aResultadoOperacion(directa()).reservaId());
    }

    @Test void aResponseCopiaTodosLosCampos() throws Exception {
        var s = new SolicitudReserva("EXT-2", "BOOKING", 5L, desde, hasta, "SUITE", 2,
                new HuespedDTO("Luz", "Gil", "luz@mail.com", "9"), new BigDecimal("77.00"), "USD");
        Reserva r = ReservaMapper.nuevaDesdeCanal(s);
        asignarId(r, 3L);
        var resp = ReservaMapper.aResponse(r);
        assertEquals(3L, resp.id());
        assertEquals("BOOKING", resp.canal());
        assertEquals("EXT-2", resp.referenciaExterna());
        assertEquals(5L, resp.hotelId());
        assertEquals("SUITE", resp.tipoHabitacion());
        assertEquals(2, resp.cantidadHabitaciones());
        assertEquals(desde, resp.checkIn());
        assertEquals(hasta, resp.checkOut());
        assertEquals("Luz", resp.huespedNombre());
        assertEquals("Gil", resp.huespedApellido());
        assertEquals("luz@mail.com", resp.huespedEmail());
        assertEquals(new BigDecimal("77.00"), resp.precioTotal());
        assertEquals("USD", resp.moneda());
        assertEquals("PENDIENTE", resp.estado());
        assertEquals(r.getFechaCreacion(), resp.fechaCreacion());
    }

    @Test void huespedDesdeDtoNuloFallaConNullPointer() {
        assertThrows(NullPointerException.class, () -> ReservaMapper.huespedDesde((HuespedDTO) null));
    }
}
