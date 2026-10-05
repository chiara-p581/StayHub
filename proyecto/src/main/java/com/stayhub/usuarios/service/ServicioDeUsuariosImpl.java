package com.stayhub.usuarios.service;

import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.ActualizacionUsuarioRequest;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.exception.CodigoErrorUsuario;
import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.messaging.EventoPassword;
import com.stayhub.usuarios.messaging.EventoUsuarioRegistrado;
import com.stayhub.usuarios.messaging.PublicadorEventoPassword;
import com.stayhub.usuarios.messaging.PublicadorEventoUsuario;
import com.stayhub.usuarios.messaging.TipoEventoPassword;
import com.stayhub.usuarios.model.Usuario;
import com.stayhub.usuarios.repository.UsuarioRepository;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.time.LocalDateTime;

@Stateless
public class ServicioDeUsuariosImpl implements ServicioDeUsuarios {

    @Inject
    private UsuarioRepository repositorio;

    @Inject
    private PasswordHasher passwordHasher;

    @Inject
    private PublicadorEventoUsuario publicadorEventos;

    @Inject
    private PublicadorEventoPassword publicadorPassword;

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

    @Override
    public UsuarioResponse actualizarPerfil(Long id, ActualizacionUsuarioRequest solicitud) {
        if (solicitud == null || solicitud.email() == null || solicitud.email().isBlank()
                || solicitud.nombre() == null || solicitud.nombre().isBlank()
                || solicitud.apellido() == null || solicitud.apellido().isBlank()
                || (solicitud.password() != null && !solicitud.password().isBlank()
                    && solicitud.password().length() < 6)) {
            throw new UsuarioException(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                    "Los datos del perfil son incompletos o inválidos");
        }
        Usuario usuario = repositorio.buscarPorId(id)
                .orElseThrow(() -> new UsuarioException(CodigoErrorUsuario.USUARIO_NO_ENCONTRADO,
                        "No existe el usuario autenticado"));
        repositorio.buscarPorEmail(solicitud.email().trim()).filter(u -> !u.getId().equals(id)).ifPresent(u -> {
            throw new UsuarioException(CodigoErrorUsuario.EMAIL_YA_REGISTRADO,
                    "Ya existe un usuario con ese email");
        });
        String nuevoHash = solicitud.password() == null || solicitud.password().isBlank()
                ? null : passwordHasher.hash(solicitud.password());
        usuario.actualizarPerfil(solicitud.email().trim(), solicitud.nombre().trim(),
                solicitud.apellido().trim(), nuevoHash);
        Usuario guardado = repositorio.guardar(usuario);

        // Si cambió la contraseña: se invalida cualquier link de recuperación pendiente y se avisa por mail.
        if (nuevoHash != null) {
            guardado.limpiarTokenRecuperacion();
            avisarPasswordCambiada(guardado);
        }
        return UsuarioMapper.aResponse(guardado);
    }

    // ---- Recuperación de contraseña ----

    @Override
    public void solicitarRecuperacionPassword(String email) {
        if (email == null || email.isBlank() || email.length() > 120
                || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new UsuarioException(CodigoErrorUsuario.SOLICITUD_INVALIDA, "Ingresá un email válido");
        }

        // Si el email no existe no hacemos nada, pero la respuesta es la misma:
        // así nadie puede averiguar qué cuentas existen.
        repositorio.buscarPorEmail(email.trim()).ifPresent(u -> {
            if (u.recuperacionReciente()) return;
            String token = TokenRecuperacion.generar();
            u.generarTokenRecuperacion(TokenRecuperacion.hash(token), LocalDateTime.now().plusHours(1));
            repositorio.guardar(u);
            publicadorPassword.publicar(new EventoPassword(u.getId(), u.getEmail(), u.getNombre(),
                    token, TipoEventoPassword.SOLICITUD_RECUPERACION));
        });
    }

    @Override
    public void resetearPassword(String token, String nuevaPassword) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")
                || nuevaPassword == null || nuevaPassword.length() < 6 || nuevaPassword.length() > 200) {
            throw new UsuarioException(CodigoErrorUsuario.SOLICITUD_INVALIDA, "Token o contraseña inválidos");
        }

        Usuario u = repositorio.buscarPorResetTokenHash(TokenRecuperacion.hash(token))
                .filter(Usuario::tokenVigente)
                .orElseThrow(() -> new UsuarioException(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                        "El enlace es inválido o expiró"));

        u.actualizarPerfil(u.getEmail(), u.getNombre(), u.getApellido(), passwordHasher.hash(nuevaPassword));
        u.limpiarTokenRecuperacion();
        repositorio.guardar(u);
        avisarPasswordCambiada(u);
    }

    private void avisarPasswordCambiada(Usuario u) {
        publicadorPassword.publicar(new EventoPassword(u.getId(), u.getEmail(), u.getNombre(),
                null, TipoEventoPassword.PASSWORD_CAMBIADA));
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