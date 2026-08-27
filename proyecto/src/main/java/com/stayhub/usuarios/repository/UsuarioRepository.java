package com.stayhub.usuarios.repository;

import com.stayhub.usuarios.model.Usuario;
import java.util.Optional;

public interface UsuarioRepository {

    Usuario guardar(Usuario usuario);

    Optional<Usuario> buscarPorId(Long id);

    Optional<Usuario> buscarPorEmail(String email);
}