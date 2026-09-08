package com.stayhub.usuarios.api;

import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsuarioResource {

    @Inject
    private ServicioDeUsuarios servicio;

    @POST
    public Response registrar(RegistroUsuarioRequest solicitud) {
        UsuarioResponse usuario = servicio.registrar(solicitud);
        return Response.status(Response.Status.CREATED)
                .location(URI.create("api/usuarios/" + usuario.id()))
                .entity(usuario)
                .build();
    }

    @POST
    @Path("/login")
    public UsuarioResponse login(LoginRequest credenciales) {
        return servicio.autenticar(credenciales);
    }

    @GET
    @Path("/{id}")
    public UsuarioResponse consultar(@PathParam("id") Long id) {
        return servicio.buscarPorId(id);
    }
}