package com.stayhub.usuarios.messaging;

import java.io.Serializable;

public record EventoPassword(
        Long usuarioId,
        String email,
        String nombre,
        String token,            // null si tipo == PASSWORD_CAMBIADA
        TipoEventoPassword tipo) implements Serializable { }