package com.stayhub.usuarios.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stayhub.seguridad.CredencialActual;
import com.stayhub.seguridad.IdentidadActual;
import com.stayhub.seguridad.MfaService;
import com.stayhub.seguridad.ProteccionLogin;
import com.stayhub.seguridad.TokenJwt;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsuarioResourceSesionTest {

    @Test
    void loginEmiteJwtEnCookieYRotaLaSesionSinGuardarLaIdentidad() throws Exception {
        var recurso = new UsuarioResource();
        ServicioDeUsuarios servicio = mock(ServicioDeUsuarios.class);
        TokenJwt tokens = mock(TokenJwt.class);
        inyectar(recurso, "servicio", servicio);
        inyectar(recurso, "proteccionLogin", mock(ProteccionLogin.class));
        inyectar(recurso, "identidad", mock(IdentidadActual.class));
        inyectar(recurso, "mfa", mock(MfaService.class));
        inyectar(recurso, "tokens", tokens);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession anterior = mock(HttpSession.class);
        HttpSession nueva = mock(HttpSession.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getContextPath()).thenReturn("/StayHub");
        when(request.getSession(false)).thenReturn(anterior);
        when(request.getSession(true)).thenReturn(nueva);
        var credenciales = new LoginRequest("sesion@stayhub.local", "StayHubSesion.2026!");
        var usuario = new UsuarioResponse(9L, credenciales.email(), "Prueba", "Sesión", "HUESPED");
        when(servicio.autenticar(credenciales)).thenReturn(usuario);
        when(tokens.emitir(eq(9L), eq("HUESPED"), eq(true), anyLong(), anyLong())).thenReturn("jwt.de.prueba");

        assertEquals(usuario, recurso.login(credenciales, request, response));

        var orden = inOrder(anterior, request, nueva);
        orden.verify(anterior).invalidate();
        orden.verify(request).getSession(true);
        orden.verify(request).changeSessionId();
        orden.verify(nueva).setMaxInactiveInterval(900);
        verify(nueva).setAttribute(eq("csrfToken"), anyString());
        // La identidad ya no vive en la sesión: viaja en el JWT.
        verify(nueva, never()).setAttribute(eq("usuarioId"), org.mockito.ArgumentMatchers.any());
        verify(nueva, never()).setAttribute(eq("usuarioRol"), org.mockito.ArgumentMatchers.any());

        ArgumentCaptor<String> cookies = ArgumentCaptor.forClass(String.class);
        verify(response, org.mockito.Mockito.atLeastOnce()).addHeader(eq("Set-Cookie"), cookies.capture());
        String cookie = cookies.getAllValues().get(cookies.getAllValues().size() - 1);
        assertTrue(cookie.startsWith(TokenJwt.COOKIE + "=jwt.de.prueba;"), cookie);
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertTrue(cookie.contains("Path=/StayHub"), cookie);
    }

    @Test
    void logoutRevocaElTokenYBorraLaCookie() throws Exception {
        var recurso = new UsuarioResource();
        TokenJwt tokens = mock(TokenJwt.class);
        inyectar(recurso, "tokens", tokens);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        var credencial = new CredencialActual(9L, "HUESPED", true, 1L, 1L, "jti-9", Long.MAX_VALUE);
        when(request.getAttribute("stayhub.credencial")).thenReturn(credencial);
        when(request.getContextPath()).thenReturn("/StayHub");

        try {
            recurso.cerrarSesion(request, response);
        } catch (RuntimeException sinServidorJaxRs) {
            // En un test unitario no hay implementación JAX-RS para armar el 204.
            // Lo importante (revocar y borrar la cookie) pasa antes; el 204 se prueba en e2e.
        }

        verify(tokens).revocar(credencial);
        ArgumentCaptor<String> cookie = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), cookie.capture());
        assertTrue(cookie.getValue().startsWith(TokenJwt.COOKIE + "=;"), cookie.getValue());
        assertTrue(cookie.getValue().contains("Max-Age=0"), cookie.getValue());
    }

    private static void inyectar(Object destino, String campo, Object valor) throws Exception {
        Field field = destino.getClass().getDeclaredField(campo);
        field.setAccessible(true);
        field.set(destino, valor);
    }
}