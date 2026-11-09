package com.stayhub.usuarios.dto;

public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        String rol,
        boolean mfaRequerido,
        boolean mfaConfigurado) {
    public UsuarioResponse(Long id, String email, String nombre, String apellido, String rol) {
        this(id, email, nombre, apellido, rol, false, false);
    }
}
