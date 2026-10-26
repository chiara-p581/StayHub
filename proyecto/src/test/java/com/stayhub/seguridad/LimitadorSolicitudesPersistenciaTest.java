package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LimitadorSolicitudesPersistenciaTest {
    @Test
    void dosInstanciasCompartenElContadorPersistente() {
        ContadorFalso compartido = new ContadorFalso();
        LimitadorSolicitudes nodoA = new LimitadorSolicitudes();
        LimitadorSolicitudes nodoB = new LimitadorSolicitudes();
        nodoA.persistente = compartido;
        nodoB.persistente = compartido;

        assertTrue(nodoA.permitir("login:cuenta:admin", 2, 1_000L, 60_000L));
        assertTrue(nodoB.permitir("login:cuenta:admin", 2, 1_001L, 60_000L));
        assertFalse(nodoA.permitir("login:cuenta:admin", 2, 1_002L, 60_000L));
        assertTrue(nodoB.bloqueada("login:cuenta:admin", 2, 1_003L, 60_000L));
    }

    private static final class ContadorFalso extends ContadoresSeguridad {
        private final Map<String, Integer> valores = new HashMap<>();
        @Override public int incrementar(String claveHash, long ahora, long ventanaMs) {
            return valores.merge(claveHash, 1, Integer::sum);
        }
        @Override public boolean bloqueada(String claveHash, int maximo, long ahora, long ventanaMs) {
            return valores.getOrDefault(claveHash, 0) >= maximo;
        }
        @Override public void eliminar(String claveHash) { valores.remove(claveHash); }
    }
}
