package com.stayhub.seguridad;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutenticacionRecuperacionTest {
    @Test void recuperacionPublicaSoloParaLasOperacionesNecesarias() {
        var filtro = new AutenticacionFilter();
        assertTrue(filtro.esPublica("usuarios/recuperar-password", "POST"));
        assertTrue(filtro.esPublica("usuarios/resetear-password", "POST"));
        assertTrue(filtro.esPublica("seguridad/csrf", "GET"));
        assertFalse(filtro.esPublica("usuarios/resetear-password", "GET"));
        assertFalse(filtro.esPublica("usuarios/recuperar-password/otro", "POST"));
        assertFalse(filtro.esPublica("usuarios/me", "PUT"));
    }
}
