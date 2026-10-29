package com.stayhub.hoteles.service;

import com.stayhub.hoteles.model.Habitacion;
import com.stayhub.hoteles.model.Hotel;
import com.stayhub.hoteles.model.TipoHabitacion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HotelMapperTest {
    private static <T> T conId(T e, Long id) {
        try {
            Field f = e.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
            return e;
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test
    void mapeaTodosLosCamposDelHotelYSusHijos() {
        Hotel h = conId(new Hotel("Sol", "Calle 1", "Mar", "AR", "desc", Set.of("Wifi")), 1L);
        TipoHabitacion t = conId(new TipoHabitacion(h, "SUITE", "Suite", "grande", 4, Set.of("TV")), 2L);
        Habitacion hab = conId(new Habitacion(h, t, "101", 5, Set.of("Vista")), 3L);

        var r = HotelMapper.hotel(h, List.of(t), List.of(hab));

        assertEquals(1L, r.id());
        assertEquals("Sol", r.nombre());
        assertEquals("Calle 1", r.direccion());
        assertEquals("Mar", r.ciudad());
        assertEquals("AR", r.pais());
        assertEquals("desc", r.descripcion());
        assertTrue(r.activo());
        assertEquals(Set.of("Wifi"), r.servicios());
        assertEquals(2L, r.tiposHabitacion().get(0).id());
        assertEquals("SUITE", r.tiposHabitacion().get(0).codigo());
        assertEquals("Suite", r.tiposHabitacion().get(0).nombre());
        assertEquals("grande", r.tiposHabitacion().get(0).descripcion());
        assertEquals(4, r.tiposHabitacion().get(0).capacidadMaxima());
        assertEquals(Set.of("TV"), r.tiposHabitacion().get(0).caracteristicas());
        var hr = r.habitaciones().get(0);
        assertEquals(3L, hr.id());
        assertEquals("101", hr.numero());
        assertEquals(5, hr.piso());
        assertTrue(hr.activa());
        assertEquals(2L, hr.tipoHabitacionId());
        assertEquals("SUITE", hr.tipoHabitacionCodigo());
        assertEquals(4, hr.capacidadMaxima());
        assertEquals(Set.of("Vista"), hr.caracteristicas());
    }

    @Test
    void reflejaEstadoInactivoYListasVacias() {
        Hotel h = conId(new Hotel("Sol", "C", "M", "AR", null, null), 1L);
        h.darDeBaja();
        TipoHabitacion t = conId(new TipoHabitacion(h, "S", "S", null, 1, null), 2L);
        t.darDeBaja();
        Habitacion hab = new Habitacion(h, t, "1", null, null);
        hab.darDeBaja();

        var r = HotelMapper.hotel(h, List.of(), List.of());

        assertFalse(r.activo());
        assertNull(r.descripcion());
        assertTrue(r.tiposHabitacion().isEmpty());
        assertTrue(r.habitaciones().isEmpty());
        assertFalse(HotelMapper.tipo(t).activo());
        var hr = HotelMapper.habitacion(hab);
        assertFalse(hr.activa());
        assertNull(hr.piso());
    }
}
