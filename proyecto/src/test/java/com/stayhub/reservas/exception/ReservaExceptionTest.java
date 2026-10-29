package com.stayhub.reservas.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReservaExceptionTest {
    @Test void conservaCodigoYMensaje() {
        var ex = new ReservaException(CodigoErrorReserva.RESERVA_NO_ENCONTRADA, "no existe");
        assertEquals(CodigoErrorReserva.RESERVA_NO_ENCONTRADA, ex.getCodigo());
        assertEquals("no existe", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test void conservaLaCausaOriginal() {
        var causa = new IllegalStateException("origen");
        var ex = new ReservaException(CodigoErrorReserva.SIN_DISPONIBILIDAD, "sin cupo", causa);
        assertEquals(CodigoErrorReserva.SIN_DISPONIBILIDAD, ex.getCodigo());
        assertSame(causa, ex.getCause());
        assertEquals("sin cupo", ex.getMessage());
    }
}
