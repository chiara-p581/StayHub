package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorInesperadoMapperTest {
    @Test void errorInternoNoExponeMensajeNiSecreto() {
        Map<String, String> cuerpo = ErrorInesperadoMapper.cuerpoSeguro(true, "correlacion-123");
        assertEquals("ERROR_INTERNO", cuerpo.get("codigo"));
        assertEquals("correlacion-123", cuerpo.get("correlationId"));
        assertFalse(cuerpo.toString().contains("super-secreta"));
        assertFalse(cuerpo.toString().contains("RuntimeException"));
    }

    @Test void errorHttpSaneaElMensaje() {
        Map<String, String> cuerpo = ErrorInesperadoMapper.cuerpoSeguro(false, "correlacion-456");
        assertEquals("SOLICITUD_INVALIDA", cuerpo.get("codigo"));
        assertFalse(cuerpo.toString().contains("dato interno"));
    }
}
