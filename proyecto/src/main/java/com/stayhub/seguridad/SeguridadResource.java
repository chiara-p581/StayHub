package com.stayhub.seguridad;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.Map;

@Path("/seguridad/csrf")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
public class SeguridadResource {
    @GET
    public Response token(@Context HttpServletRequest request) {
        return Response.ok(Map.of("token", Csrf.token(request.getSession(true))))
                .header("Cache-Control", "no-store").build();
    }
}
