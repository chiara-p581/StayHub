package com.stayhub.pagos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PagoTest {

    @Test
    void constructorCompletoInicializaPendienteConSusDatos() {
        LocalDateTime antes = LocalDateTime.now();
        Pago p = new Pago(4L, List.of(7L, 8L), new BigDecimal("30.00"), "ARS", "clave-1234567890123");

        assertEquals(4L, p.getUsuarioId());
        assertEquals(7L, p.getReservaId());
        assertEquals(List.of(7L, 8L), p.getReservaIds());
        assertEquals(new BigDecimal("30.00"), p.getMonto());
        assertEquals("ARS", p.getMoneda());
        assertEquals("clave-1234567890123", p.getClaveIdempotencia());
        assertEquals(EstadoPago.PENDIENTE, p.getEstado());
        assertNull(p.getReferenciaPasarela());
        assertNull(p.getId());
        assertFalse(p.getFechaPago().isBefore(antes));
    }

    @Test
    void constructorDeUnaReservaNoTieneUsuarioNiClave() {
        Pago p = new Pago(9L, BigDecimal.TEN, "USD");

        assertNull(p.getUsuarioId());
        assertNull(p.getClaveIdempotencia());
        assertEquals(9L, p.getReservaId());
        assertEquals(List.of(9L), p.getReservaIds());
        assertEquals(EstadoPago.PENDIENTE, p.getEstado());
    }

    @Test
    void constructorDeListaSinClave() {
        Pago p = new Pago(List.of(1L, 2L), BigDecimal.ONE, "ARS");

        assertEquals(1L, p.getReservaId());
        assertEquals(List.of(1L, 2L), p.getReservaIds());
        assertNull(p.getClaveIdempotencia());
    }

    @Test
    void constructorDeListaConClave() {
        Pago p = new Pago(List.of(1L), BigDecimal.ONE, "ARS", "clave-1234567890123");

        assertEquals("clave-1234567890123", p.getClaveIdempotencia());
        assertNull(p.getUsuarioId());
    }

    @Test
    void aprobarGuardaLaReferenciaDeLaPasarela() {
        Pago p = new Pago(1L, BigDecimal.TEN, "ARS");

        p.aprobar("MOCK-1");

        assertEquals(EstadoPago.APROBADO, p.getEstado());
        assertEquals("MOCK-1", p.getReferenciaPasarela());
    }

    @Test
    void rechazarNoGuardaReferencia() {
        Pago p = new Pago(1L, BigDecimal.TEN, "ARS");

        p.rechazar();

        assertEquals(EstadoPago.RECHAZADO, p.getEstado());
        assertNull(p.getReferenciaPasarela());
    }

    @Test
    void listaVaciaDeReservasEsInvalida() {
        assertThrows(IndexOutOfBoundsException.class, () -> new Pago(List.of(), BigDecimal.TEN, "ARS"));
    }

    @Test
    void listaNulaDeReservasEsInvalida() {
        assertThrows(NullPointerException.class, () -> new Pago((List<Long>) null, BigDecimal.TEN, "ARS"));
    }

    @Test
    void lasReservasDevueltasSonUnaCopiaInmutable() {
        Pago p = new Pago(List.of(1L, 2L), BigDecimal.TEN, "ARS");

        assertThrows(UnsupportedOperationException.class, () -> p.getReservaIds().add(3L));
        assertEquals(2, p.getReservaIds().size());
    }

    @Test
    void modificarLaListaOriginalNoAfectaAlPago() {
        var origen = new ArrayList<>(List.of(1L, 2L));
        Pago p = new Pago(origen, BigDecimal.TEN, "ARS");

        origen.add(99L);

        assertEquals(List.of(1L, 2L), p.getReservaIds());
    }
}
