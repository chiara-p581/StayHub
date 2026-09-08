package com.stayhub.usuarios.dto;

import com.stayhub.usuarios.model.RolUsuario;

public record RegistroUsuarioRequest(
        String email,
        String password,
        String nombre,
        String apellido,
        RolUsuario rol) { }