package com.stayhub.inventarioytarifas.model;

import com.stayhub.inventarioytarifas.exception.CodigoErrorInventarioTarifas;
import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de dominio de InventarioDiario, Hold y EstadoHold. */
class InventarioDiarioHoldTest {

    private final LocalDate fecha = LocalDate.now().plusDays(3);

    private InventarioDiario dia(int totales) {
        return new InventarioDiario(7L, "DOBLE", fecha, totales, new BigDecimal("150.50"), "ARS");
    }

    private Hold hold() {
        return new Hold("h-1", 7L, "DOBLE", 2, fecha, fecha.plusDays(2));
    }

    // ---------------- InventarioDiario ----------------

    @Test
    void inventarioNuevoTieneTodasLasUnidadesDisponiblesYSinOcupadas() {
        InventarioDiario d = dia(5);
        assertEquals(5, d.getUnidadesDisponibles());
        assertEquals(0, d.getUnidadesOcupadas());
        assertEquals(5, d.getUnidadesTotales());
        assertEquals(7L, d.getHotelId());
        assertEquals("DOBLE", d.getTipoHabitacion());
        assertEquals(fecha, d.getFecha());
        assertEquals(new BigDecimal("150.50"), d.getPrecio());
        assertEquals("ARS", d.getMoneda());
        assertNull(d.getId());
    }

    @Test
    void ocuparDescuentaDisponibilidadYAcumula() {
        InventarioDiario d = dia(5);
        d.ocupar(2);
        d.ocupar(1);
        assertEquals(3, d.getUnidadesOcupadas());
        assertEquals(2, d.getUnidadesDisponibles());
    }

    @Test
    void ocuparExactamenteLoDisponibleEsValidoYDejaCeroDisponibles() {
        InventarioDiario d = dia(3);
        d.ocupar(3);
        assertEquals(0, d.getUnidadesDisponibles());
    }

    @Test
    void ocuparUnaUnidadMasDeLoDisponibleLanzaSobreventaYNoMutaEstado() {
        InventarioDiario d = dia(3);
        d.ocupar(2);
        InventarioTarifasException ex = assertThrows(InventarioTarifasException.class, () -> d.ocupar(2));
        assertEquals(CodigoErrorInventarioTarifas.SOBREVENTA_DETECTADA, ex.getCodigo());
        assertTrue(ex.getMessage().contains("solo hay 1 disponible/s"));
        assertTrue(ex.getMessage().contains("hotelId=7"));
        assertEquals(2, d.getUnidadesOcupadas());
    }

    @Test
    void ocuparEnInventarioSinUnidadesFallaSiempre() {
        InventarioDiario d = dia(0);
        InventarioTarifasException ex = assertThrows(InventarioTarifasException.class, () -> d.ocupar(1));
        assertEquals(CodigoErrorInventarioTarifas.SOBREVENTA_DETECTADA, ex.getCodigo());
    }

    @Test
    void liberarDevuelveUnidades() {
        InventarioDiario d = dia(5);
        d.ocupar(4);
        d.liberar(3);
        assertEquals(1, d.getUnidadesOcupadas());
        assertEquals(4, d.getUnidadesDisponibles());
    }

    @Test
    void liberarMasDeLoOcupadoNuncaDejaOcupadasNegativas() {
        InventarioDiario d = dia(5);
        d.ocupar(1);
        d.liberar(10);
        assertEquals(0, d.getUnidadesOcupadas());
        assertEquals(5, d.getUnidadesDisponibles());
    }

    @Test
    void actualizarCapacidadYTarifaCambiaTotalesPrecioYMonedaSinTocarOcupadas() {
        InventarioDiario d = dia(5);
        d.ocupar(2);
        d.actualizarCapacidadYTarifa(8, new BigDecimal("99.99"), "USD");
        assertEquals(8, d.getUnidadesTotales());
        assertEquals(new BigDecimal("99.99"), d.getPrecio());
        assertEquals("USD", d.getMoneda());
        assertEquals(2, d.getUnidadesOcupadas());
        assertEquals(6, d.getUnidadesDisponibles());
    }

    @Test
    void reducirCapacidadPorDebajoDeLoOcupadoDaDisponibilidadNegativaYBloqueaOcupar() {
        InventarioDiario d = dia(5);
        d.ocupar(4);
        d.actualizarCapacidadYTarifa(2, BigDecimal.ONE, "ARS");
        assertEquals(-2, d.getUnidadesDisponibles());
        assertThrows(InventarioTarifasException.class, () -> d.ocupar(1));
    }

    // ---------------- Hold ----------------

    @Test
    void holdNuevoNaceEnPendienteConDatosYFechaDeCreacion() {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        Hold h = hold();
        assertEquals("h-1", h.getId());
        assertEquals(7L, h.getHotelId());
        assertEquals("DOBLE", h.getTipoHabitacion());
        assertEquals(2, h.getCantidadHabitaciones());
        assertEquals(fecha, h.getCheckIn());
        assertEquals(fecha.plusDays(2), h.getCheckOut());
        assertEquals(EstadoHold.PENDIENTE, h.getEstado());
        assertNotNull(h.getFechaCreacion());
        assertFalse(h.getFechaCreacion().isBefore(antes));
        assertNull(h.getFechaActualizacion());
    }

    @Test
    void confirmarPasaAConfirmadoYRegistraActualizacion() {
        Hold h = hold();
        h.confirmar();
        assertEquals(EstadoHold.CONFIRMADO, h.getEstado());
        assertNotNull(h.getFechaActualizacion());
        assertFalse(h.getFechaActualizacion().isBefore(h.getFechaCreacion()));
    }

    @Test
    void liberarPasaALiberadoDesdePendienteYDesdeConfirmado() {
        Hold pendiente = hold();
        pendiente.liberar();
        assertEquals(EstadoHold.LIBERADO, pendiente.getEstado());
        assertNotNull(pendiente.getFechaActualizacion());

        Hold confirmado = hold();
        confirmado.confirmar();
        confirmado.liberar();
        assertEquals(EstadoHold.LIBERADO, confirmado.getEstado());
    }

    @Test
    void estadoHoldTieneLosTresEstadosDelCicloDeVida() {
        assertArrayEquals(new EstadoHold[]{EstadoHold.PENDIENTE, EstadoHold.CONFIRMADO, EstadoHold.LIBERADO},
                EstadoHold.values());
        assertEquals(EstadoHold.CONFIRMADO, EstadoHold.valueOf("CONFIRMADO"));
        assertThrows(IllegalArgumentException.class, () -> EstadoHold.valueOf("VENCIDO"));
    }
}
