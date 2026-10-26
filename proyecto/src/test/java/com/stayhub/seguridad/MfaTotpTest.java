package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class MfaTotpTest {
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final long INSTANTE = 59L;

    @Test
    void validaTotpYRechazaLaReutilizacionDelMismoCodigo() {
        MfaTotp mfa = mfaFija();
        String codigo = mfa.codigo(SECRETO_RFC, INSTANTE);

        assertDoesNotThrow(() -> mfa.verificar(7L, codigo));
        SeguridadException repetido = assertThrows(SeguridadException.class,
                () -> mfa.verificar(7L, codigo));
        assertEquals(401, repetido.getEstado());
    }

    @Test
    void rechazaCodigoIncorrectoYConfiguracionAusente() {
        MfaTotp mfa = mfaFija();
        assertEquals(401, assertThrows(SeguridadException.class,
                () -> mfa.verificar(7L, "000000")).getEstado());

        MfaTotp sinSecreto = new MfaTotp(Clock.fixed(Instant.ofEpochSecond(INSTANTE), ZoneOffset.UTC));
        assertEquals(503, assertThrows(SeguridadException.class,
                () -> sinSecreto.verificar(8L, "287082")).getEstado());
    }

    private MfaTotp mfaFija() {
        MfaTotp mfa = new MfaTotp(Clock.fixed(Instant.ofEpochSecond(INSTANTE), ZoneOffset.UTC)) {
            @Override String secreto(Long usuarioId) { return SECRETO_RFC; }
        };
        mfa.limitador = new LimitadorSolicitudes();
        return mfa;
    }
}
