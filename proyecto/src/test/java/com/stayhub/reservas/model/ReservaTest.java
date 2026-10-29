package com.stayhub.reservas.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservaTest {
    LocalDate desde = LocalDate.now().plusDays(3), hasta = desde.plusDays(2);
    Reserva reserva;

    @BeforeEach void preparar() {
        reserva = new Reserva("BOOKING", "REF-9", 4L, "DOBLE", 2, desde, hasta,
                new Huesped("Ana", "Paz", "ana@mail.com", "555"), new BigDecimal("200.00"), "USD");
    }

    @Test void constructorDejaLaReservaPendienteConTodosLosDatos() {
        assertEquals(EstadoReserva.PENDIENTE, reserva.getEstado());
        assertEquals("BOOKING", reserva.getCanal());
        assertEquals("REF-9", reserva.getReferenciaExterna());
        assertEquals(4L, reserva.getHotelId());
        assertEquals("DOBLE", reserva.getTipoHabitacion());
        assertEquals(2, reserva.getCantidadHabitaciones());
        assertEquals(desde, reserva.getCheckIn());
        assertEquals(hasta, reserva.getCheckOut());
        assertEquals(new BigDecimal("200.00"), reserva.getPrecioTotal());
        assertEquals("USD", reserva.getMoneda());
        assertEquals("Ana", reserva.getHuesped().getNombre());
        assertEquals("Paz", reserva.getHuesped().getApellido());
        assertEquals("ana@mail.com", reserva.getHuesped().getEmail());
        assertEquals("555", reserva.getHuesped().getTelefono());
        assertNull(reserva.getHoldId());
        assertNull(reserva.getId());
        assertNull(reserva.getUsuarioId());
        assertNull(reserva.getFechaActualizacion());
        assertFalse(reserva.getFechaCreacion().isAfter(LocalDateTime.now()));
        assertTrue(reserva.getFechaCreacion().isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test void iniciarHoldGuardaElHoldYMantienePendiente() {
        reserva.iniciarHold("h-1");
        assertEquals("h-1", reserva.getHoldId());
        assertEquals(EstadoReserva.PENDIENTE, reserva.getEstado());
        assertNotNull(reserva.getFechaActualizacion());
    }

    @Test void confirmarCambiaEstadoYReemplazaElHold() {
        reserva.iniciarHold("h-1");
        reserva.confirmar("h-2");
        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
        assertEquals("h-2", reserva.getHoldId());
        assertNotNull(reserva.getFechaActualizacion());
    }

    @Test void rechazarMarcaRechazadaYConservaElHold() {
        reserva.iniciarHold("h-1");
        reserva.rechazar();
        assertEquals(EstadoReserva.RECHAZADA, reserva.getEstado());
        assertEquals("h-1", reserva.getHoldId());
        assertNotNull(reserva.getFechaActualizacion());
    }

    @Test void cancelarMarcaCanceladaYRegistraActualizacion() {
        reserva.cancelar();
        assertEquals(EstadoReserva.CANCELADA, reserva.getEstado());
        assertNotNull(reserva.getFechaActualizacion());
    }

    @Test void actualizarDatosReemplazaCamposYPasaAModificada() {
        LocalDate nuevoIn = desde.plusDays(10), nuevoOut = nuevoIn.plusDays(1);
        reserva.actualizarDatos(nuevoIn, nuevoOut, "SUITE", 3, new BigDecimal("999.99"));
        assertEquals(EstadoReserva.MODIFICADA, reserva.getEstado());
        assertEquals(nuevoIn, reserva.getCheckIn());
        assertEquals(nuevoOut, reserva.getCheckOut());
        assertEquals("SUITE", reserva.getTipoHabitacion());
        assertEquals(3, reserva.getCantidadHabitaciones());
        assertEquals(new BigDecimal("999.99"), reserva.getPrecioTotal());
        // lo que no se actualiza se conserva
        assertEquals(4L, reserva.getHotelId());
        assertEquals("USD", reserva.getMoneda());
        assertNotNull(reserva.getFechaActualizacion());
    }

    @Test void asignarPropietarioGuardaElUsuario() {
        reserva.asignarPropietario(8L);
        assertEquals(8L, reserva.getUsuarioId());
    }

    @Test void propietarioEsInmutable() {
        reserva.asignarPropietario(8L);
        var error = assertThrows(IllegalStateException.class, () -> reserva.asignarPropietario(9L));
        assertEquals("Propietario inmutable", error.getMessage());
        assertEquals(8L, reserva.getUsuarioId());
    }

    @Test void propietarioNuloEsInvalido() {
        var error = assertThrows(IllegalStateException.class, () -> reserva.asignarPropietario(null));
        assertEquals("Propietario inmutable", error.getMessage());
        assertNull(reserva.getUsuarioId());
    }

    @Test void estadosDelCicloDeVidaSonLosDocumentados() {
        assertArrayEquals(new EstadoReserva[]{EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA,
                EstadoReserva.MODIFICADA, EstadoReserva.CANCELADA, EstadoReserva.RECHAZADA}, EstadoReserva.values());
        assertEquals(EstadoReserva.CANCELADA, EstadoReserva.valueOf("CANCELADA"));
    }
}
