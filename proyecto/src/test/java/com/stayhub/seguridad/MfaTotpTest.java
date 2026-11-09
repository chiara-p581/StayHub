package com.stayhub.seguridad;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MfaTotpTest {
    @Test void coincideConVectorRfc6238ReducidoASeisDigitos() {
        assertEquals("287082", MfaTotp.generar("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1));
    }
    @Test void rechazaFormatoInvalido() { assertFalse(MfaTotp.validar("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "123")); }
}
