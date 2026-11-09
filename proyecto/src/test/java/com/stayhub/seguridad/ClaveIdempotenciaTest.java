package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaveIdempotenciaTest {
    @Test void normalizaYCombinaHeaderConCuerpo() {
        assertEquals("operacion-segura-0001", ClaveIdempotencia.combinar(
                " operacion-segura-0001 ", "operacion-segura-0001"));
    }

    @Test void rechazaAusenciaFormatoInseguroYValoresDistintos() {
        assertThrows(SeguridadException.class, () -> ClaveIdempotencia.requerida(" "));
        assertThrows(SeguridadException.class, () -> ClaveIdempotencia.normalizar("corta"));
        assertThrows(SeguridadException.class, () -> ClaveIdempotencia.normalizar("x".repeat(81)));
        assertThrows(SeguridadException.class, () -> ClaveIdempotencia.combinar(
                "operacion-segura-0001", "operacion-segura-0002"));
    }
}
