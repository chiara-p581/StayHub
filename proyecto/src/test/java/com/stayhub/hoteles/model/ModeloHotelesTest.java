package com.stayhub.hoteles.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModeloHotelesTest {
    @Test
    void hotelNuevoEstaActivoYSinId() {
        Hotel h = new Hotel("A", "B", "C", "D", "E", Set.of("Wifi"));
        assertTrue(h.isActivo());
        assertNull(h.getId());
        assertEquals("A", h.getNombre());
        assertEquals("B", h.getDireccion());
        assertEquals("C", h.getCiudad());
        assertEquals("D", h.getPais());
        assertEquals("E", h.getDescripcion());
        assertEquals(Set.of("Wifi"), h.getServicios());
    }

    @Test
    void hotelActualizarReemplazaServiciosYAceptaNull() {
        Hotel h = new Hotel("A", "B", "C", "D", "E", Set.of("Wifi", "Spa"));
        h.actualizar("A2", "B2", "C2", "D2", null, Set.of("Pileta"));
        assertEquals("A2", h.getNombre());
        assertNull(h.getDescripcion());
        assertEquals(Set.of("Pileta"), h.getServicios());
        h.actualizar("A2", "B2", "C2", "D2", null, null);
        assertTrue(h.getServicios().isEmpty());
    }

    @Test
    void hotelDarDeBajaEsIdempotente() {
        Hotel h = new Hotel("A", "B", "C", "D", null, null);
        h.darDeBaja();
        h.darDeBaja();
        assertFalse(h.isActivo());
    }

    @Test
    void hotelDevuelveCopiaInmutableDeServicios() {
        Hotel h = new Hotel("A", "B", "C", "D", null, new LinkedHashSet<>(Set.of("Wifi")));
        assertThrows(UnsupportedOperationException.class, () -> h.getServicios().add("Otro"));
        assertEquals(Set.of("Wifi"), h.getServicios());
    }

    @Test
    void tipoHabitacionGuardaDatosYSePuedeActualizarYDarDeBaja() {
        Hotel h = new Hotel("A", "B", "C", "D", null, null);
        TipoHabitacion t = new TipoHabitacion(h, "DBL", "Doble", "desc", 2, Set.of("TV"));
        assertSame(h, t.getHotel());
        assertEquals("DBL", t.getCodigo());
        assertEquals("Doble", t.getNombre());
        assertEquals("desc", t.getDescripcion());
        assertEquals(2, t.getCapacidadMaxima());
        assertTrue(t.isActivo());
        assertNull(t.getId());

        t.actualizar("TPL", "Triple", null, 3, null);
        assertEquals("TPL", t.getCodigo());
        assertEquals(3, t.getCapacidadMaxima());
        assertNull(t.getDescripcion());
        assertTrue(t.getCaracteristicas().isEmpty());

        t.darDeBaja();
        assertFalse(t.isActivo());
        assertThrows(UnsupportedOperationException.class, () -> t.getCaracteristicas().add("x"));
    }

    @Test
    void habitacionCanonizaNumeroAMayusculasYSinEspacios() {
        Hotel h = new Hotel("A", "B", "C", "D", null, null);
        TipoHabitacion t = new TipoHabitacion(h, "DBL", "Doble", null, 2, null);
        Habitacion hab = new Habitacion(h, t, "  ab-1 ", 2, Set.of("Vista"));

        assertEquals("AB-1", hab.getNumero());
        assertSame(h, hab.getHotel());
        assertSame(t, hab.getTipo());
        assertEquals(2, hab.getPiso());
        assertTrue(hab.isActiva());
        assertEquals(Set.of("Vista"), hab.getCaracteristicas());
    }

    @Test
    void habitacionActualizarPermiteNumeroNuloYLimpiaCaracteristicas() {
        Hotel h = new Hotel("A", "B", "C", "D", null, null);
        TipoHabitacion t = new TipoHabitacion(h, "DBL", "Doble", null, 2, null);
        TipoHabitacion t2 = new TipoHabitacion(h, "TPL", "Triple", null, 3, null);
        Habitacion hab = new Habitacion(h, t, "1", 1, Set.of("Vista"));

        hab.actualizar(t2, null, null, null);

        assertNull(hab.getNumero());
        assertNull(hab.getPiso());
        assertSame(t2, hab.getTipo());
        assertTrue(hab.getCaracteristicas().isEmpty());
        hab.darDeBaja();
        assertFalse(hab.isActiva());
        assertThrows(UnsupportedOperationException.class, () -> hab.getCaracteristicas().add("x"));
    }
}
