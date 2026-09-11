package com.stayhub.usuarios.api;

import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.model.RolUsuario;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;

@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsuarioResource {

    @Inject
    private ServicioDeUsuarios servicio;

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
        UsuarioResponse usuario = servicio.autenticar(credenciales);
        HttpSession sesion = request.getSession(true);
        request.changeSessionId();
        sesion.setAttribute("usuarioId", usuario.id());
        sesion.setAttribute("usuarioRol", usuario.rol());
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
        return servicio.buscarPorId(id);
    }
}
