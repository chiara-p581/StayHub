package com.stayhub.seguridad;

import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
public class SeguridadExceptionMapper implements ExceptionMapper<SeguridadException> {
    public Response toResponse(SeguridadException ex) {
        return Response.status(ex.getEstado()).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("codigo", "SOLICITUD_RECHAZADA", "mensaje", ex.getMessage())).build();
    }
}
