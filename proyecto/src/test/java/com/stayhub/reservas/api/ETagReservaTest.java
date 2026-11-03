package com.stayhub.reservas.api;

import static org.junit.jupiter.api.Assertions.*;

import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.service.ServicioDeReservas;
import org.junit.jupiter.api.Test;

class ETagReservaTest {

    @Test
    void formateaComoStrongETagEntreComillas() {
        assertEquals("\"3\"", ETagReserva.formatear(3L));
        assertEquals("\"0\"", ETagReserva.formatear(null));
    }

    @Test
    void interpretaLasVariantesValidasDeIfMatch() {
        assertEquals(3L, ETagReserva.versionDesdeIfMatch("\"3\""));
        assertEquals(3L, ETagReserva.versionDesdeIfMatch(" W/\"3\" "));
        assertEquals(3L, ETagReserva.versionDesdeIfMatch("3"));
        assertEquals(ServicioDeReservas.CUALQUIER_VERSION, ETagReserva.versionDesdeIfMatch("*"));
    }

    @Test
    void sinHeaderDevuelveNull() {
        assertNull(ETagReserva.versionDesdeIfMatch(null));
        assertNull(ETagReserva.versionDesdeIfMatch("  "));
    }

    @Test
    void valoresInvalidosSonSolicitudInvalida() {
        for (String malo : new String[] {"\"abc\"", "\"1\", \"2\"", "\"-5\"", "\""}) {
            var error = assertThrows(ReservaException.class, () -> ETagReserva.versionDesdeIfMatch(malo), malo);
            assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
            assertEquals(400, ReservaExceptionMapper.estadoHttp(error));
        }
    }

    @Test
    void codigosDeConcurrenciaMapeanA412y428() {
        assertEquals(412, ReservaExceptionMapper.estadoHttp(new ReservaException(CodigoErrorReserva.VERSION_OBSOLETA, "x")));
        assertEquals(428, ReservaExceptionMapper.estadoHttp(new ReservaException(CodigoErrorReserva.VERSION_REQUERIDA, "x")));
    }
}
