package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class LimitadorLoginTest {
    @Test void limitaIntentosPorOrigen() {
        var limite = new LimitadorLogin(Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC));
        for (int i = 0; i < LimitadorLogin.MAX_INTENTOS; i++) assertTrue(limite.permitir("203.0.113.10"));
        assertFalse(limite.permitir("203.0.113.10"));
        assertTrue(limite.permitir("203.0.113.11"));
    }
}
