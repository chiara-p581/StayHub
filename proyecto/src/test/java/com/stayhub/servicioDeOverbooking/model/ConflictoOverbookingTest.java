package com.stayhub.servicioDeOverbooking.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConflictoOverbookingTest {

    private final LocalDate in = LocalDate.of(2026, 12, 1);
    private final LocalDate out = LocalDate.of(2026, 12, 4);

    private ConflictoOverbooking nuevo() {
        return new ConflictoOverbooking(13L, 33L, "STANDARD", 2, in, out, "BOOKING", "REF-1", "ana@test.com");
    }

    @Test
    void nuevoConflictoQuedaSinResolverConSusDatos() {
        LocalDateTime antes = LocalDateTime.now();

        ConflictoOverbooking c = nuevo();

        assertNull(c.getId());
        assertEquals(13L, c.getReservaIdConflictiva());
        assertEquals(33L, c.getHotelId());
        assertEquals("STANDARD", c.getTipoHabitacion());
        assertEquals(2, c.getCantidadHabitaciones());
        assertEquals(in, c.getCheckIn());
        assertEquals(out, c.getCheckOut());
        assertEquals("BOOKING", c.getCanalOrigen());
        assertEquals("REF-1", c.getReferenciaExterna());
        assertEquals("ana@test.com", c.getHuespedEmail());
        assertFalse(c.isResuelto());
        assertNull(c.getEstrategiaAplicada());
        assertNull(c.getHoldAlternativoId());
        assertNull(c.getTipoHabitacionAlternativa());
        assertNull(c.getMensaje());
        assertFalse(c.getFechaCreacion().isBefore(antes));
    }

    @Test
    void reubicacionGuardaAlternativaHoldYMensaje() {
        ConflictoOverbooking c = nuevo();

        c.resolverPorReubicacion("SUITE", "hold-7", "Reubicado");

        assertTrue(c.isResuelto());
        assertEquals(EstrategiaResolucion.REUBICACION, c.getEstrategiaAplicada());
        assertEquals("SUITE", c.getTipoHabitacionAlternativa());
        assertEquals("hold-7", c.getHoldAlternativoId());
        assertEquals("Reubicado", c.getMensaje());
    }

    @Test
    void compensacionNoDejaAlternativaNiHold() {
        ConflictoOverbooking c = nuevo();

        c.resolverPorCompensacion("Se ofrece compensación");

        assertTrue(c.isResuelto());
        assertEquals(EstrategiaResolucion.COMPENSACION, c.getEstrategiaAplicada());
        assertNull(c.getTipoHabitacionAlternativa());
        assertNull(c.getHoldAlternativoId());
        assertEquals("Se ofrece compensación", c.getMensaje());
    }

    @Test
    void unaCompensacionPosteriorSobrescribeLaEstrategiaPeroConservaLaAlternativaPrevia() {
        ConflictoOverbooking c = nuevo();
        c.resolverPorReubicacion("SUITE", "hold-7", "Reubicado");

        c.resolverPorCompensacion("Cambio");

        assertEquals(EstrategiaResolucion.COMPENSACION, c.getEstrategiaAplicada());
        assertEquals("Cambio", c.getMensaje());
        assertEquals("hold-7", c.getHoldAlternativoId());
    }

    @Test
    void camposOpcionalesPuedenSerNulos() {
        ConflictoOverbooking c = new ConflictoOverbooking(1L, 2L, "X", 1, in, out, null, null, null);

        assertNull(c.getCanalOrigen());
        assertNull(c.getReferenciaExterna());
        assertNull(c.getHuespedEmail());
        assertFalse(c.isResuelto());
    }
}
