package com.stayhub.seguridad;

import com.stayhub.usuarios.api.UsuarioResource;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutenticacionRecuperacionTest {
    @Test void recuperacionPublicaSoloParaLasOperacionesNecesarias() {
        assertNotNull(metodo(UsuarioResource.class, "recuperar").getAnnotation(PermitAll.class));
        assertNotNull(metodo(UsuarioResource.class, "resetear").getAnnotation(PermitAll.class));
        assertNotNull(SeguridadResource.class.getAnnotation(PermitAll.class));
        assertNull(metodo(UsuarioResource.class, "actualizarMiPerfil").getAnnotation(PermitAll.class));
    }

    private static java.lang.reflect.Method metodo(Class<?> tipo, String nombre) {
        return java.util.Arrays.stream(tipo.getDeclaredMethods())
                .filter(m -> m.getName().equals(nombre)).findFirst().orElseThrow();
    }
}
