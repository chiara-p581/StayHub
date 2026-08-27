package com.stayhub.usuarios.dto;

public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        String rol) { }