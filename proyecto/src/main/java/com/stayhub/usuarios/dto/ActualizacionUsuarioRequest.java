package com.stayhub.usuarios.dto;

public record ActualizacionUsuarioRequest(
        String email,
        String nombre,
        String apellido,
        String password) { }
