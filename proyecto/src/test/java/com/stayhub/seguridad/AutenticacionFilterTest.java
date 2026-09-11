package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutenticacionFilterTest {
    private final AutenticacionFilter filtro = new AutenticacionFilter();

    @Test
    void dejaPublicosCatalogoLoginYTarifas() {
        assertTrue(filtro.esPublica("usuarios", "POST"));
        assertTrue(filtro.esPublica("usuarios/login", "POST"));
        assertTrue(filtro.esPublica("hoteles", "GET"));
        assertTrue(filtro.esPublica("inventario-tarifas/tarifas", "GET"));
        assertFalse(filtro.esPublica("carrito/hotel", "POST"));
    }

    @Test
    void normalizaLasVariantesDeRutaQuePuedeEntregarWildFly() {
        assertEquals("usuarios", filtro.normalizarRuta("/usuarios/"));
        assertEquals("usuarios", filtro.normalizarRuta("api/usuarios"));
        assertEquals("usuarios/login", filtro.normalizarRuta("/api/usuarios/login/"));
    }

    @Test
    void reservaOperacionesAdministrativasParaAdmin() {
        assertTrue(filtro.requiereAdmin("hoteles", "POST"));
        assertTrue(filtro.requiereAdmin("canales-externos/disponibilidad", "GET"));
        assertTrue(filtro.requiereAdmin("overbooking/resolver", "POST"));
        assertTrue(filtro.requiereAdmin("demo/cargar", "POST"));
        assertFalse(filtro.requiereAdmin("carrito/confirmar", "POST"));
    }

    @Test
    void reconoceSoloLasRutasDeWebhookOta() {
        assertTrue(filtro.esWebhookOta("canales-externos/otas/BOOKING/reservas"));
        assertTrue(filtro.esWebhookOta("canales-externos/otas/BOOKING/reservas/BK-1"));
        assertFalse(filtro.esWebhookOta("canales-externos/otas/BOOKING/sincronizaciones"));
    }
}
