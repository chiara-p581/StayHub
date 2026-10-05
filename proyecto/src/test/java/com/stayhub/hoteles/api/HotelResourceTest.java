package com.stayhub.hoteles.api;

import com.stayhub.hoteles.exception.CodigoErrorHotel;
import com.stayhub.hoteles.exception.HotelException;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotelResourceTest {
    @Test
    void rechazaIdsTextualesConElContratoDeHoteles() {
        HotelException error = assertThrows(HotelException.class,
                () -> HotelResource.parsearId("no-es-un-id", "hotel"));

        assertEquals(CodigoErrorHotel.SOLICITUD_INVALIDA, error.getCodigo());
        assertFalse(error.getMessage().contains("NumberFormatException"));
    }

    @Test
    void reconoceElErrorJsonEnvueltoPorResteasy() {
        ProcessingException error = new ProcessingException("detalle interno",
                new JsonbException("tipo incorrecto"));

        assertTrue(JsonProcessingExceptionMapper.esErrorDeJson(error));
    }
}
