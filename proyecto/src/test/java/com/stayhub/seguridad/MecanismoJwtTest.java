package com.stayhub.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Lo que hace MecanismoJwt con cada pedido: validar, marcar Bearer y rotar la cookie. */
class MecanismoJwtTest {

    private static final byte[] CLAVE = "clave-de-prueba-de-32-bytes-o-mas!!".getBytes(StandardCharsets.UTF_8);
    private static final long AHORA = 1_900_000_000_000L;

    private static TokenJwt tokensEn(long millis) {
        return new TokenJwt(CLAVE, Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC));
    }

    private static MecanismoJwt mecanismo(TokenJwt tokens) {
        MecanismoJwt mecanismo = new MecanismoJwt();
        mecanismo.tokens = tokens;
        return mecanismo;
    }

    /** Request falso que guarda de verdad los atributos que le ponen. */
    private static HttpServletRequest request(String authorization, String cookie) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Map<String, Object> atributos = new HashMap<>();
        when(request.getAttribute(anyString())).thenAnswer(i -> atributos.get((String) i.getArgument(0)));
        doAnswer(i -> { atributos.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(request).setAttribute(anyString(), any());
        when(request.getHeader("Authorization")).thenReturn(authorization);
        if (cookie != null) {
            Cookie c = mock(Cookie.class);
            when(c.getName()).thenReturn(TokenJwt.COOKIE);
            when(c.getValue()).thenReturn(cookie);
            when(request.getCookies()).thenReturn(new Cookie[] {c});
        }
        when(request.getContextPath()).thenReturn("/StayHub");
        return request;
    }

    private static HttpMessageContext contexto() {
        HttpMessageContext contexto = mock(HttpMessageContext.class);
        when(contexto.doNothing()).thenReturn(AuthenticationStatus.NOT_DONE);
        when(contexto.notifyContainerAboutLogin(any(java.security.Principal.class), any())).thenReturn(AuthenticationStatus.SUCCESS);
        return contexto;
    }

    @Test
    void bearerValidoAutenticaYQuedaMarcadoParaNoPedirCsrf() {
        TokenJwt tokens = tokensEn(AHORA);
        String token = tokens.emitir(7L, "HUESPED", true, AHORA, 0L);
        HttpServletRequest request = request("Bearer " + token, null);

        assertEquals(AuthenticationStatus.SUCCESS,
                mecanismo(tokens).validateRequest(request, mock(HttpServletResponse.class), contexto()));
        assertEquals(7L, CredencialActual.de(request).usuarioId());
        assertTrue(TokenJwt.autenticadoConBearer(request));
    }

    @Test
    void bearerInvalidoNoAutenticaNiDesactivaElCsrf() {
        HttpServletRequest request = request("Bearer token.alterado.x", null);

        assertEquals(AuthenticationStatus.NOT_DONE,
                mecanismo(tokensEn(AHORA)).validateRequest(request, mock(HttpServletResponse.class), contexto()));
        assertNull(CredencialActual.de(request));
        assertTrue(CredencialActual.tokenInvalido(request));
        assertFalse(TokenJwt.autenticadoConBearer(request));
    }

    @Test
    void cookieQueEstaPorVencerSeRenuevaYLaAnteriorQuedaRevocada() {
        String viejo = tokensEn(AHORA).emitir(7L, "HUESPED", true, AHORA, 0L);
        long casiVencido = AHORA + TokenJwt.DURACION_MS - 60_000L; // le queda 1 minuto
        TokenJwt tokens = tokensEn(casiVencido);
        HttpServletRequest request = request(null, viejo);
        HttpServletResponse response = mock(HttpServletResponse.class);

        mecanismo(tokens).validateRequest(request, response, contexto());

        ArgumentCaptor<String> cookie = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), cookie.capture());
        String nuevo = cookie.getValue().substring((TokenJwt.COOKIE + "=").length(), cookie.getValue().indexOf(';'));
        assertEquals(7L, tokens.validar(nuevo).usuarioId());
        // El token viejo deja de valer pasada la gracia, aunque todavía no haya llegado a su exp.
        assertThrows(TokenJwt.TokenInvalidoException.class,
                () -> tokensEn(casiVencido + TokenJwt.GRACIA_ROTACION_MS + 1).validar(viejo));
    }

    @Test
    void cookieConTiempoDeSobraNoSeRenueva() {
        TokenJwt tokens = tokensEn(AHORA);
        HttpServletRequest request = request(null, tokens.emitir(7L, "HUESPED", true, AHORA, 0L));
        HttpServletResponse response = mock(HttpServletResponse.class);

        mecanismo(tokens).validateRequest(request, response, contexto());

        verify(response, never()).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void sinTokenNoHaceNada() {
        HttpServletRequest request = request(null, null);
        assertEquals(AuthenticationStatus.NOT_DONE,
                mecanismo(tokensEn(AHORA)).validateRequest(request, mock(HttpServletResponse.class), contexto()));
        assertNull(CredencialActual.de(request));
    }
}
