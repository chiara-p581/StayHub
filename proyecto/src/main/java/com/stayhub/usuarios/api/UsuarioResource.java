package com.stayhub.usuarios.api;

import com.stayhub.seguridad.Csrf;
import com.stayhub.seguridad.IdentidadActual;
import com.stayhub.seguridad.SeguridadException;
import com.stayhub.seguridad.ProteccionLogin;
import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.ActualizacionUsuarioRequest;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.ResetPasswordRequest;
import com.stayhub.usuarios.dto.SolicitudRecuperacionRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.model.RolUsuario;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;

@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsuarioResource {
    private static final String INICIO_ABSOLUTO_SESION = "inicioAbsolutoSesion";

    @Inject
    private ServicioDeUsuarios servicio;

    @Inject
    private ProteccionLogin proteccionLogin;

    @Inject
    private IdentidadActual identidad;

    @POST
    public Response registrar(RegistroUsuarioRequest solicitud) {
        RegistroUsuarioRequest registroPublico = solicitud == null ? null : new RegistroUsuarioRequest(
                solicitud.email(), solicitud.password(), solicitud.nombre(), solicitud.apellido(), RolUsuario.HUESPED);
        UsuarioResponse usuario = servicio.registrar(registroPublico);
        return Response.status(Response.Status.CREATED)
                .location(URI.create("api/usuarios/" + usuario.id()))
                .entity(usuario)
                .build();
    }

    @POST
    @Path("/login")
    public UsuarioResponse login(LoginRequest credenciales, @Context HttpServletRequest request) {
        String email = credenciales == null ? null : credenciales.email();
        String ip = request.getRemoteAddr();
        proteccionLogin.verificar(email, ip);
        UsuarioResponse usuario;
        try {
            usuario = servicio.autenticar(credenciales);
        } catch (UsuarioException ex) {
            proteccionLogin.registrarFallo(email, ip);
            throw ex;
        }
        proteccionLogin.registrarExito(email, ip);
        HttpSession anterior = request.getSession(false);
        if (anterior != null) anterior.invalidate();
        HttpSession sesion = request.getSession(true);
        request.changeSessionId();
        sesion.setMaxInactiveInterval(900);
        sesion.setAttribute("usuarioId", usuario.id());
        sesion.setAttribute("usuarioRol", usuario.rol());
        sesion.setAttribute(INICIO_ABSOLUTO_SESION, System.currentTimeMillis());
        Csrf.token(sesion);
        return usuario;
    }

    @DELETE
    @Path("/sesion")
    public Response cerrarSesion(@Context HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) sesion.invalidate();
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}")
    public UsuarioResponse consultar(@PathParam("id") Long id) {
        // SEC-W06 (BOLA): el perfil de otra persona responde 404, igual que un id
        // inexistente, para no confirmar qué usuarios existen. La identidad sale de
        // la sesión (IdentidadActual), nunca de datos que mande el cliente.
        Long usuarioId = identidad.usuarioId();
        if (!usuarioId.equals(id) && !identidad.admin()) {
            throw new SeguridadException(404, "Usuario no disponible");
        }
        return servicio.buscarPorId(id);
    }

    @PUT
    @Path("/me")
    public UsuarioResponse actualizarMiPerfil(ActualizacionUsuarioRequest solicitud,
                                              @Context HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null || sesion.getAttribute("usuarioId") == null) {
            throw new WebApplicationException("Iniciá sesión para continuar", Response.Status.UNAUTHORIZED);
        }
        UsuarioResponse actualizado = servicio.actualizarPerfil((Long) sesion.getAttribute("usuarioId"), solicitud);
        sesion.setAttribute("usuarioRol", actualizado.rol());
        return actualizado;
    }

    // ---- Recuperación de contraseña (públicos: se usan sin estar logueado) ----

    @POST
    @Path("/recuperar-password")
    public Response recuperar(SolicitudRecuperacionRequest s) {
        if (s == null) throw new BadRequestException("Falta la solicitud");
        servicio.solicitarRecuperacionPassword(s.email());
        // Misma respuesta exista o no la cuenta
        return Response.accepted(Map.of("mensaje",
                "Si existe la cuenta, recibirás un enlace de recuperación")).build();
    }

    @POST
    @Path("/resetear-password")
    public Response resetear(ResetPasswordRequest s) {
        if (s == null) throw new BadRequestException("Falta la solicitud");
        servicio.resetearPassword(s.token(), s.nuevaPassword());
        return Response.noContent().build();
    }
}