package com.stayhub.usuarios.service;

import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.exception.CodigoErrorUsuario;
import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.messaging.EventoUsuarioRegistrado;
import com.stayhub.usuarios.messaging.PublicadorEventoUsuario;
import com.stayhub.usuarios.model.Usuario;
import com.stayhub.usuarios.repository.UsuarioRepository;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
public class ServicioDeUsuariosImpl implements ServicioDeUsuarios {

    @Inject
    private UsuarioRepository repositorio;

    @Inject
    private PasswordHasher passwordHasher;

    @Inject
    private PublicadorEventoUsuario publicadorEventos;

    @Override
    public UsuarioResponse registrar(RegistroUsuarioRequest solicitud) {
        validar(solicitud);

        if (repositorio.buscarPorEmail(solicitud.email()).isPresent()) {
            throw new UsuarioException(CodigoErrorUsuario.EMAIL_YA_REGISTRADO,
                    "Ya existe un usuario con ese email");
        }

        String hash = passwordHasher.hash(solicitud.password());
        Usuario usuario = new Usuario(solicitud.email(), hash, solicitud.nombre(),
                solicitud.apellido(), solicitud.rol());
        repositorio.guardar(usuario);

        publicadorEventos.publicarUsuarioRegistrado(new EventoUsuarioRegistrado(
                usuario.getId(), usuario.getEmail(), usuario.getNombre(), usuario.getRol().name()));

        return UsuarioMapper.aResponse(usuario);
    }

    @Override
    public UsuarioResponse autenticar(LoginRequest credenciales) {
        Usuario usuario = repositorio.buscarPorEmail(credenciales.email())
                .orElseThrow(() -> new UsuarioException(CodigoErrorUsuario.CREDENCIALES_INVALIDAS,
                        "Email o contraseña incorrectos"));

        if (!passwordHasher.verificar(credenciales.password(), usuario.getPasswordHash())) {
            throw new UsuarioException(CodigoErrorUsuario.CREDENCIALES_INVALIDAS,
                    "Email o contraseña incorrectos");
        }

        return UsuarioMapper.aResponse(usuario);
    }

    @Override
    public UsuarioResponse buscarPorId(Long id) {
        Usuario usuario = repositorio.buscarPorId(id)
                .orElseThrow(() -> new UsuarioException(CodigoErrorUsuario.USUARIO_NO_ENCONTRADO,
                        "No existe un usuario con id " + id));
        return UsuarioMapper.aResponse(usuario);
    }

    private void validar(RegistroUsuarioRequest s) {
        if (s == null || s.email() == null || s.email().isBlank()
                || s.password() == null || s.password().length() < 6
                || s.nombre() == null || s.nombre().isBlank()
                || s.rol() == null) {
            throw new UsuarioException(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                    "La solicitud de registro está incompleta o contiene valores inválidos");
        }
    }
}