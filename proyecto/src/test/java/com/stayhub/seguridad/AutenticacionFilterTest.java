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
        assertTrue(filtro.esPublica("catalogo-canales/ofertas", "GET"));
        assertTrue(filtro.esPublica("inventario-tarifas/tarifas", "GET"));
        assertFalse(filtro.esPublica("carrito/hotel", "POST"));
    }

    @Test
    void identificaComprasQueUnAdminNoPuedeRealizar() {
        assertTrue(filtro.esCompra("carrito/confirmar", "POST"));
        assertTrue(filtro.esCompra("reservas", "POST"));
        assertTrue(filtro.esCompra("pagos", "POST"));
        assertFalse(filtro.esCompra("pagos/2", "GET"));
        assertFalse(filtro.esCompra("reservas", "GET"));
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

    @Test
    void aplicaVencimientoAbsolutoALaSesion() {
        long ahora = System.currentTimeMillis();
        assertTrue(filtro.inicioSesionExpirado(ahora - 9L * 60 * 60 * 1000, ahora));
        assertFalse(filtro.inicioSesionExpirado(ahora - 60_000L, ahora));
        assertTrue(filtro.inicioSesionExpirado(null, ahora));
    }

    @Test
    void antesDeValidarMfaSoloPermiteConfigurarloOCerrarSesion() {
        assertTrue(filtro.esFlujoMfaPermitido("usuarios/mfa/preparar", "POST"));
        assertTrue(filtro.esFlujoMfaPermitido("usuarios/mfa/activar", "POST"));
        assertTrue(filtro.esFlujoMfaPermitido("usuarios/mfa/estado", "GET"));
        assertTrue(filtro.esFlujoMfaPermitido("usuarios/sesion", "DELETE"));
        assertFalse(filtro.esFlujoMfaPermitido("usuarios/1", "GET"));
        assertFalse(filtro.esFlujoMfaPermitido("hoteles", "POST"));
    }

    @Test
    void protegeLaAdministracionDeCodigosYElReemplazoDelAutenticador() {
        assertTrue(filtro.requiereReautenticacion("usuarios/mfa/codigos-recuperacion/regenerar", "POST"));
        assertTrue(filtro.requiereReautenticacion("usuarios/mfa/reiniciar", "POST"));
        assertFalse(filtro.requiereReautenticacion("usuarios/mfa/estado", "GET"));
    }
}
