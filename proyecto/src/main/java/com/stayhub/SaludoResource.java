package com.stayhub;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/saludo")
public class SaludoResource {

    @Inject
    private SaludoService servicio;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String saludar() {
        return servicio.mensaje();
    }
}
