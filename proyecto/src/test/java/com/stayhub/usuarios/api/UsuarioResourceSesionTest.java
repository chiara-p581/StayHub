package com.stayhub.usuarios.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stayhub.seguridad.IdentidadActual;
import com.stayhub.seguridad.ProteccionLogin;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class UsuarioResourceSesionTest {

    @Test
    void loginInvalidaLaSesionAnteriorYRotaLaNueva() throws Exception {
        var recurso = new UsuarioResource();
        ServicioDeUsuarios servicio = mock(ServicioDeUsuarios.class);
        ProteccionLogin proteccion = mock(ProteccionLogin.class);
        inyectar(recurso, "servicio", servicio);
        inyectar(recurso, "proteccionLogin", proteccion);
        inyectar(recurso, "identidad", mock(IdentidadActual.class));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession anterior = mock(HttpSession.class);
        HttpSession nueva = mock(HttpSession.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getSession(false)).thenReturn(anterior);
        when(request.getSession(true)).thenReturn(nueva);
        var credenciales = new LoginRequest("sesion@stayhub.local", "StayHubSesion.2026!");
        var usuario = new UsuarioResponse(9L, credenciales.email(), "Prueba", "Sesión", "HUESPED");
        when(servicio.autenticar(credenciales)).thenReturn(usuario);

        assertEquals(usuario, recurso.login(credenciales, request));

        var orden = inOrder(anterior, request, nueva);
        orden.verify(anterior).invalidate();
        orden.verify(request).getSession(true);
        orden.verify(request).changeSessionId();
        orden.verify(nueva).setMaxInactiveInterval(900);
        verify(nueva).setAttribute("usuarioId", 9L);
        verify(nueva).setAttribute("usuarioRol", "HUESPED");
        verify(nueva).setAttribute(eq("inicioAbsolutoSesion"), anyLong());
        verify(nueva).setAttribute(eq("csrfToken"), anyString());
    }

    private static void inyectar(Object destino, String campo, Object valor) throws Exception {
        Field field = destino.getClass().getDeclaredField(campo);
        field.setAccessible(true);
        field.set(destino, valor);
    }
}
