package com.stayhub.usuarios.service;

import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.model.Usuario;

final class UsuarioMapper {

    private UsuarioMapper() { }

    static UsuarioResponse aResponse(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getEmail(), u.getNombre(), u.getApellido(), u.getRol().name());
    }
}