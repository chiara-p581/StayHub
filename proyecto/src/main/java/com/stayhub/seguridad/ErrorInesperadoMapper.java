package com.stayhub.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** Última barrera: evita exponer excepciones internas y entrega un ID rastreable. */
@Provider
public class ErrorInesperadoMapper implements ExceptionMapper<Throwable> {
    @Context HttpServletRequest request;

    @Override
    public Response toResponse(Throwable error) {
        int estado = error instanceof WebApplicationException web ? web.getResponse().getStatus() : 500;
        boolean interno = estado >= 500;
        if (interno) AuditoriaSeguridad.error(request, error);
        else AuditoriaSeguridad.registrarUnaVez(request, "SOLICITUD_RECHAZADA", "RECHAZADO",
                request == null ? "desconocido" : request.getRequestURI(), "HTTP_" + estado);
        String id = AuditoriaSeguridad.correlationId(request);
        return Response.status(estado)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(cuerpoSeguro(interno, id))
                .build();
    }

    static Map<String, String> cuerpoSeguro(boolean interno, String correlationId) {
        return Map.of(
                "codigo", interno ? "ERROR_INTERNO" : "SOLICITUD_INVALIDA",
                "mensaje", interno ? "An unexpected error occurred." : "The request could not be processed.",
                "correlationId", correlationId);
    }
}
