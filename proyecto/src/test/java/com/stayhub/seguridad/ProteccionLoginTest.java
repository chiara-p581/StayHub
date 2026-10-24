package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class ProteccionLoginTest {
    @Test void bloqueaElSextoFalloPorCuentaEIp() {
        var proteccion = new ProteccionLogin(Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC));
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> proteccion.verificar("huesped@stayhub.test", "203.0.113.1"));
            proteccion.registrarFallo("huesped@stayhub.test", "203.0.113.1");
        }
        SeguridadException error = assertThrows(SeguridadException.class,
                () -> proteccion.verificar("huesped@stayhub.test", "203.0.113.1"));
        assertEquals(429, error.getEstado());
        assertThrows(SeguridadException.class,
                () -> proteccion.verificar("otra@stayhub.test", "203.0.113.1"));
        assertThrows(SeguridadException.class,
                () -> proteccion.verificar("huesped@stayhub.test", "203.0.113.2"));
    }

    @Test void unLoginExitosoLimpiaLosFallos() {
        var proteccion = new ProteccionLogin(Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC));
        for (int i = 0; i < 4; i++) proteccion.registrarFallo("huesped@stayhub.test", "203.0.113.1");
        proteccion.registrarExito("huesped@stayhub.test", "203.0.113.1");
        assertDoesNotThrow(() -> proteccion.verificar("huesped@stayhub.test", "203.0.113.1"));
    }
}