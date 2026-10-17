package com.stayhub.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
public class SeguridadExceptionMapper implements ExceptionMapper<SeguridadException> {
    @Context HttpServletRequest request;

    public Response toResponse(SeguridadException ex) {
        AuditoriaSeguridad.registrar(request, "SOLICITUD_RECHAZADA", "RECHAZADO",
                request == null ? "desconocido" : request.getRequestURI(), "HTTP_" + ex.getEstado());
        return Response.status(ex.getEstado()).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("codigo", "SOLICITUD_RECHAZADA", "mensaje", ex.getMessage(),
                        "correlationId", AuditoriaSeguridad.correlationId(request))).build();
    }
}
