package com.stayhub.seguridad;

/**
 * Nombres de los roles que entiende el contenedor (los mismos valores que
 * {@code RolUsuario}). Son constantes para poder usarlas dentro de
 * {@code @RolesAllowed} y {@code @DeclareRoles} sin repetir textos sueltos.
 */
public final class Roles {
    public static final String HUESPED = "HUESPED";
    public static final String ADMIN = "ADMIN";

    private Roles() { }
}