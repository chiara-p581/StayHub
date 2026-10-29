package com.stayhub.seguridad;

import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.service.ServicioDeNotificacionesImpl;
import com.stayhub.pagos.dto.PagoLoteRequest;
import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.service.ServicioDePagosImpl;
import com.stayhub.usuarios.dto.ActualizacionUsuarioRequest;
import com.stayhub.usuarios.service.ServicioDeUsuariosImpl;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-W07: las operaciones sensibles de Pagos, Usuarios y Notificaciones
 * declaran qué rol las puede ejecutar. Si alguien borra la anotación, falla el build.
 */
class SeguridadDeclarativaTest {

    private static String[] roles(Method metodo) {
        RolesAllowed enMetodo = metodo.getAnnotation(RolesAllowed.class);
        RolesAllowed enClase = metodo.getDeclaringClass().getAnnotation(RolesAllowed.class);
        RolesAllowed efectiva = enMetodo != null ? enMetodo : enClase;
        assertNotNull(efectiva, metodo + " debe tener @RolesAllowed");
        return efectiva.value();
    }

    @Test
    void pagarSoloPuedeHacerloUnHuesped() throws Exception {
        assertArrayEquals(new String[]{Roles.HUESPED},
                roles(ServicioDePagosImpl.class.getMethod("procesarPago", PagoRequest.class)));
        assertArrayEquals(new String[]{Roles.HUESPED},
                roles(ServicioDePagosImpl.class.getMethod("procesarLote", PagoLoteRequest.class)));
    }

    @Test
    void editarPerfilRequiereUsuarioAutenticado() throws Exception {
        assertArrayEquals(new String[]{Roles.HUESPED, Roles.ADMIN},
                roles(ServicioDeUsuariosImpl.class.getMethod("actualizarPerfil", Long.class,
                        ActualizacionUsuarioRequest.class)));
    }

    @Test
    void envioManualDeNotificacionesSoloAdmin() throws Exception {
        assertArrayEquals(new String[]{Roles.ADMIN},
                roles(ServicioDeNotificacionesImpl.class.getMethod("enviar", SolicitudNotificacionDTO.class)));
    }

    @Test
    void registroYLoginSiguenSiendoPublicos() {
        // Sin @PermitAll de clase, WildFly negaría los métodos sin anotar (registro, login, consultas internas).
        assertNotNull(ServicioDeUsuariosImpl.class.getAnnotation(PermitAll.class));
        assertNotNull(ServicioDePagosImpl.class.getAnnotation(PermitAll.class));
    }
}