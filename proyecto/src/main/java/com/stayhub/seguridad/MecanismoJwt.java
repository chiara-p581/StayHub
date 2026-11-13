package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.CallerPrincipal;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Autenticación con JWT para todo StayHub (Jakarta Security).
 *
 * <p>En cada pedido toma el token del header {@code Authorization: Bearer ...} o de la
 * cookie HttpOnly {@code STAYHUB_TOKEN}, lo valida con {@link TokenJwt} y:</p>
 * <ul>
 *   <li>si es válido, deja la {@link CredencialActual} en el request y le informa al
 *       contenedor el usuario y su rol, así funcionan los {@code @RolesAllowed};</li>
 *   <li>si es inválido (vencido, alterado, {@code alg=none}, de otro emisor o revocado),
 *       lo marca y {@code AutenticacionFilter} responde 401 en las rutas protegidas;</li>
 *   <li>si le quedan menos de 5 minutos y vino en la cookie, emite uno nuevo
 *       (la sesión se mantiene mientras se usa, con tope de 8 horas desde el login).</li>
 * </ul>
 *
 * <p>Reemplaza a MecanismoSesionContenedor: la identidad ya no vive en la sesión HTTP
 * (la sesión solo guarda el token CSRF y el paso pendiente de MFA del login).
 * GRASP: Fabricación pura e Indirección. SOLID: el resto del código no sabe si la
 * identidad vino de un token o de otro mecanismo (variaciones protegidas).</p>
 *
 * <p>Requiere {@code integrated-jaspi=false} (ver {@code config/seguridad-declarativa.cli}).
 * Si falta, la aplicación igual funciona con la credencial del request, pero los
 * {@code @RolesAllowed} responden 403 (fallar de forma segura).</p>
 */
@ApplicationScoped
public class MecanismoJwt implements HttpAuthenticationMechanism {
    private static final Logger LOG = Logger.getLogger(MecanismoJwt.class.getName());
    private final AtomicBoolean avisoConfiguracion = new AtomicBoolean();

    @Inject
    TokenJwt tokens;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
                                                HttpMessageContext contexto) {
        String token = TokenJwt.extraer(request);
        if (token == null || token.isBlank()) return contexto.doNothing(); // anónimo: rutas públicas
        CredencialActual credencial;
        try {
            credencial = tokens.validar(token);
        } catch (TokenJwt.TokenInvalidoException ex) {
            request.setAttribute(CredencialActual.ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE);
            return contexto.doNothing();
        }
        request.setAttribute(CredencialActual.ATRIBUTO, credencial);
        renovarSiHaceFalta(request, response, credencial);
        try {
            return contexto.notifyContainerAboutLogin(new CallerPrincipal(String.valueOf(credencial.usuarioId())),
                    Set.of(credencial.rol()));
        } catch (RuntimeException ex) {
            if (avisoConfiguracion.compareAndSet(false, true)) {
                LOG.warning("Seguridad declarativa inactiva: ejecutá config/seguridad-declarativa.cli "
                        + "(integrated-jaspi=false) y reiniciá WildFly.");
            }
            return contexto.doNothing();
        }
    }

    private void renovarSiHaceFalta(HttpServletRequest request, HttpServletResponse response, CredencialActual c) {
        if (TokenJwt.usaHeaderAuthorization(request)) return; // los clientes de API piden otro token con login
        if (c.expiraEn() - System.currentTimeMillis() > TokenJwt.RENOVAR_ANTES_DE_MS) return;
        TokenJwt.escribirCookie(request, response, tokens.reemitir(c, c.rol(), c.mfaVerificada(), c.reautenticadoEn()));
    }
}