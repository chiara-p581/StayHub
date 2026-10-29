package com.stayhub.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.CallerPrincipal;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Puente entre el login propio de StayHub (sesión HTTP) y la seguridad del
 * contenedor (Jakarta Security).
 *
 * <p>El login sigue siendo el de {@code UsuarioResource}: valida la contraseña
 * contra la base y guarda {@code usuarioId} y {@code usuarioRol} en la sesión.
 * En cada request este mecanismo le informa al contenedor quién es el usuario y
 * qué rol tiene. Así funcionan {@code @RolesAllowed} en los EJB y
 * {@code getCallerPrincipal()}, sin el popup BASIC ni usuarios en el
 * ApplicationRealm (los motivos por los que se había quitado la seguridad
 * declarativa).</p>
 *
 * <p>GRASP: Fabricación Pura e Indirección (una clase inventada solo para
 * desacoplar la sesión del contenedor). SOLID: responsabilidad única.</p>
 *
 * <p>Requiere {@code integrated-jaspi=false} en WildFly
 * (ver {@code config/seguridad-declarativa.cli}). Si falta, no rompe la
 * aplicación: la request sigue como anónima y los métodos con
 * {@code @RolesAllowed} responden 403 (fallar de forma segura).</p>
 */
@ApplicationScoped
public class MecanismoSesionContenedor implements HttpAuthenticationMechanism {
    private static final Logger LOG = Logger.getLogger(MecanismoSesionContenedor.class.getName());
    private final AtomicBoolean avisoConfiguracion = new AtomicBoolean();

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
                                                HttpMessageContext contexto) {
        HttpSession sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute("usuarioId");
        Object rol = sesion == null ? null : sesion.getAttribute("usuarioRol");
        if (!(id instanceof Long usuarioId) || !(rol instanceof String nombreRol) || !rolValido(nombreRol)) {
            return contexto.doNothing(); // anónimo: los recursos públicos siguen funcionando
        }
        try {
            return contexto.notifyContainerAboutLogin(new CallerPrincipal(String.valueOf(usuarioId)),
                    Set.of(nombreRol));
        } catch (RuntimeException ex) {
            if (avisoConfiguracion.compareAndSet(false, true)) {
                LOG.warning("Seguridad declarativa inactiva: ejecutá config/seguridad-declarativa.cli "
                        + "(integrated-jaspi=false) y reiniciá WildFly.");
            }
            return contexto.doNothing();
        }
    }

    private static boolean rolValido(String rol) {
        return Roles.HUESPED.equals(rol) || Roles.ADMIN.equals(rol);
    }
}