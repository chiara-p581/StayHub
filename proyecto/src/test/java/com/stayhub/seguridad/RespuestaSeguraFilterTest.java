package com.stayhub.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class RespuestaSeguraFilterTest {

    record ErrorOriginal(String codigo, String mensaje) {}

    static Stream<Arguments> estadosRequeridos() {
        return Stream.of(400, 401, 403, 404, 409, 422, 429, 500, 503)
                .map(estado -> Arguments.of(estado, RespuestaSeguraFilter.titulo(estado)));
    }

    @ParameterizedTest(name = "HTTP {0} usa Problem Details")
    @MethodSource("estadosRequeridos")
    void generaProblemDetailsParaCadaEstadoRequerido(int estado, String titulo) {
        Map<String, Object> problema = RespuestaSeguraFilter.problema(
                estado, Map.of(), "api/prueba", "corr-1");

        assertEquals(estado, problema.get("status"));
        assertEquals(titulo, problema.get("title"));
        assertEquals("/api/prueba", problema.get("instance"));
        assertEquals("corr-1", problema.get("correlationId"));
        assertTrue(problema.get("type").toString().startsWith("urn:stayhub:problem:"));
        assertFalse(problema.get("detail").toString().isBlank());
    }

    @Test
    void conservaCodigoYMensajeSeguroDeLosMappersExistentes() {
        Map<String, Object> problema = RespuestaSeguraFilter.problema(409,
                new ErrorOriginal("TRANSICION_INVALIDA", "El estado actual no permite la operación"),
                "api/reservas/1", "corr-2");

        assertEquals("TRANSICION_INVALIDA", problema.get("codigo"));
        assertEquals("El estado actual no permite la operación", problema.get("detail"));
        assertEquals(problema.get("detail"), problema.get("mensaje"));
    }

    @Test
    void reemplazaTodoDetalleInternoEnErroresDelServidor() {
        Map<String, Object> problema = RespuestaSeguraFilter.problema(500,
                Map.of("codigo", "SQL_ERROR", "mensaje", "password=secreto en com.zaxxer.Driver"),
                "api/pagos", "corr-3");

        assertEquals("ERROR_INTERNO", problema.get("codigo"));
        assertFalse(problema.toString().contains("secreto"));
        assertFalse(problema.toString().contains("com.zaxxer"));
    }

    @Test
    void descartaCodigosNoControladosYAcotaMensajes() {
        String largo = "x".repeat(600) + "\u0000";
        Map<String, Object> problema = RespuestaSeguraFilter.problema(400,
                Map.of("codigo", "../../interno", "mensaje", largo), "api/prueba", null);

        assertEquals("HTTP_400", problema.get("codigo"));
        assertEquals(500, problema.get("detail").toString().length());
        assertEquals("sin-correlacion", problema.get("correlationId"));
    }
}
