package com.stayhub.usuarios.contrato;

import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;

public interface ServicioDeUsuarios {

    UsuarioResponse registrar(RegistroUsuarioRequest solicitud);

    UsuarioResponse autenticar(LoginRequest credenciales);

    UsuarioResponse buscarPorId(Long id);
}