package com.stayhub.seguridad;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Map;

/** Política única para toda respuesta REST de error, incluso las creadas por mappers de componentes. */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class RespuestaSeguraFilter implements ContainerResponseFilter {
    @Context HttpServletRequest request;

    @Override
    public void filter(ContainerRequestContext solicitud, ContainerResponseContext respuesta) throws IOException {
        String correlacion = AuditoriaSeguridad.correlationId(request);
        respuesta.getHeaders().putSingle(AuditoriaSeguridad.HEADER_CORRELACION, correlacion);
        respuesta.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        if (respuesta.getStatus() < 400) return;

        respuesta.getHeaders().putSingle("Cache-Control", "no-store");
        AuditoriaSeguridad.registrarUnaVez(request, "RESPUESTA_ERROR", "RECHAZADO",
                solicitud.getUriInfo().getPath(), "HTTP_" + respuesta.getStatus());

        // Ningún mapper puede filtrar mensajes, clases o referencias internas en respuestas 5xx.
        if (respuesta.getStatus() >= 500) {
            respuesta.setEntity(Map.of("codigo", "ERROR_INTERNO",
                    "mensaje", "An unexpected error occurred.", "correlationId", correlacion));
            respuesta.getHeaders().putSingle("Content-Type", MediaType.APPLICATION_JSON);
        }
    }
}