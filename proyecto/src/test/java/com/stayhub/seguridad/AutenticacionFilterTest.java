package com.stayhub.seguridad;

import com.stayhub.canalesexternos.api.CanalExternoResource;
import com.stayhub.hoteles.api.HotelResource;
import com.stayhub.inventarioytarifas.api.InventarioResource;
import com.stayhub.reservas.api.ReservaResource;
import com.stayhub.usuarios.api.UsuarioResource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AutenticacionFilterTest {
    private final AutenticacionFilter filtro = new AutenticacionFilter();

    @Test
    void normalizaLasVariantesDeRutaQuePuedeEntregarWildFly() {
        assertEquals("usuarios", filtro.normalizarRuta("/usuarios/"));
        assertEquals("usuarios", filtro.normalizarRuta("api/usuarios"));
        assertEquals("usuarios/login", filtro.normalizarRuta("/api/usuarios/login/"));
    }

    @Test
    void aplicaVencimientoAbsolutoYReautenticacionALaSesion() {
        long ahora = System.currentTimeMillis();
        assertTrue(filtro.inicioSesionExpirado(ahora - 9L * 60 * 60 * 1000, ahora));
        assertFalse(filtro.inicioSesionExpirado(ahora - 60_000L, ahora));
        assertTrue(filtro.inicioSesionExpirado(null, ahora));

        var sesion = IdentidadFixture.sesion(1L, "ADMIN");
        sesion.setAttribute(SesionSegura.ADMIN_REAUTENTICADO_EN, ahora - 60_000L);
        assertTrue(filtro.reauthReciente(sesion, ahora));
        sesion.setAttribute(SesionSegura.ADMIN_REAUTENTICADO_EN,
                ahora - SesionSegura.REAUTENTICACION_ADMIN_MS - 1);
        assertFalse(filtro.reauthReciente(sesion, ahora));
    }

    @Test
    void autorizacionEstaDeclaradaEnLosRecursosNoEnRutasDelFiltro() {
        assertNotNull(HotelResource.class.getAnnotation(PermitAll.class));
        assertTiene(HotelResource.class, "crear", RolesAllowed.class, ReautenticacionReciente.class);
        assertNotNull(InventarioResource.class.getAnnotation(PermitAll.class));
        assertTiene(InventarioResource.class, "cargar", RolesAllowed.class, ReautenticacionReciente.class);
        assertTiene(ReservaResource.class, "crear", RolesAllowed.class);
        assertTiene(ReservaResource.class, "listarPorHotel", RolesAllowed.class);
        assertTiene(UsuarioResource.class, "login", PermitAll.class);
        assertTiene(UsuarioResource.class, "reautenticar", RolesAllowed.class);
        assertTiene(CanalExternoResource.class, "recibir", WebhookFirmado.class);
        assertTiene(CanalExternoResource.class, "sincronizarOta", ReautenticacionReciente.class);
    }

    @SafeVarargs
    private static void assertTiene(Class<?> tipo, String nombre,
                                    Class<? extends java.lang.annotation.Annotation>... anotaciones) {
        Method metodo = Arrays.stream(tipo.getDeclaredMethods())
                .filter(m -> m.getName().equals(nombre)).findFirst().orElseThrow();
        for (Class<? extends java.lang.annotation.Annotation> anotacion : anotaciones)
            assertNotNull(metodo.getAnnotation(anotacion), nombre + " requiere @" + anotacion.getSimpleName());
    }
}
