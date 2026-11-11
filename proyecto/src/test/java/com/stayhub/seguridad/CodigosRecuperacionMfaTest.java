package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodigosRecuperacionMfaTest {
    @Test
    void generaOchoCodigosUnicosConFormatoSeguro() {
        List<String> codigos = CodigosRecuperacionMfa.generar();
        assertEquals(8, codigos.size());
        assertEquals(8, new HashSet<>(codigos).size());
        assertTrue(codigos.stream().allMatch(CodigosRecuperacionMfa::tieneFormato));
    }

    @Test
    void normalizaAntesDeHashear() {
        assertEquals(CodigosRecuperacionMfa.hash("ABCD-EFGH-JKLM"),
                CodigosRecuperacionMfa.hash("  abcd-efgh-jklm  "));
        assertFalse(CodigosRecuperacionMfa.tieneFormato("000000"));
    }
}
