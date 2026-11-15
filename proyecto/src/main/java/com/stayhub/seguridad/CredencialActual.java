package com.stayhub.seguridad;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Identidad de la persona que hace el pedido, tal como viene en su JWT ya
 * validado (firma, emisor, audiencia, vencimiento y revocación).
 *
 * <p>{@link MecanismoJwt} la guarda como atributo del request; el resto del
 * sistema la lee desde acá y nunca desde datos que mande el cliente.</p>
 *
 * @param usuarioId       id del usuario (claim {@code sub})
 * @param rol             HUESPED o ADMIN (claim {@code rol})
 * @param mfaVerificada   si el administrador ya validó su segundo factor (claim {@code mfa})
 * @param autenticadoEn   cuándo hizo login, en milisegundos (claim {@code auth_time})
 * @param reautenticadoEn última confirmación de contraseña + MFA, en milisegundos; 0 si no hubo
 * @param jti             id único del token, para poder revocarlo en el logout
 * @param expiraEn        vencimiento del token, en milisegundos (claim {@code exp})
 */
public record CredencialActual(Long usuarioId, String rol, boolean mfaVerificada, long autenticadoEn,
                               long reautenticadoEn, String jti, long expiraEn) {

    static final String ATRIBUTO = "stayhub.credencial";
    static final String ATRIBUTO_TOKEN_INVALIDO = "stayhub.tokenInvalido";

    /** La credencial del request, o {@code null} si no trajo un JWT válido. */
    public static CredencialActual de(HttpServletRequest request) {
        Object valor = request == null ? null : request.getAttribute(ATRIBUTO);
        return valor instanceof CredencialActual credencial ? credencial : null;
    }

    /** {@code true} si el request trajo un JWT pero no pasó la validación. */
    public static boolean tokenInvalido(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(ATRIBUTO_TOKEN_INVALIDO));
    }

    public boolean admin() {
        return Roles.ADMIN.equals(rol);
    }
}