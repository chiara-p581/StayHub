package com.stayhub.reservas.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HuespedTest {
    @Test void constructorCompletoConservaLosCuatroDatos() {
        var h = new Huesped("Ana", "Paz", "ana@mail.com", "555");
        assertEquals("Ana", h.getNombre());
        assertEquals("Paz", h.getApellido());
        assertEquals("ana@mail.com", h.getEmail());
        assertEquals("555", h.getTelefono());
    }

    @Test void constructorParaJpaDejaTodoEnNulo() {
        var h = new Huesped();
        assertNull(h.getNombre());
        assertNull(h.getApellido());
        assertNull(h.getEmail());
        assertNull(h.getTelefono());
    }

    @Test void reservaParaJpaNaceSinEstadoNiDatos() {
        var r = new Reserva();
        assertNull(r.getEstado());
        assertNull(r.getId());
        assertNull(r.getHotelId());
        assertEquals(0, r.getCantidadHabitaciones());
    }
}
