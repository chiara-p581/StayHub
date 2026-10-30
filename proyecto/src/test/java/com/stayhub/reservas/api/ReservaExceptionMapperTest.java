package com.stayhub.reservas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import org.junit.jupiter.api.Test;

class ReservaExceptionMapperTest {

    @Test
    void unaTransicionInvalidaSeExponeComoConflict() {
        var error = new ReservaException(
                CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA,
                "No se puede cambiar una reserva de CANCELADA a CONFIRMADA");

        assertEquals(409, ReservaExceptionMapper.estadoHttp(error));
    }
}
