package com.stayhub.seguridad;

import jakarta.ejb.EJBAccessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Cuando el contenedor rechaza una llamada por {@code @RolesAllowed} lanza
 * {@link EJBAccessException}. Sin este mapper se vería como error 500; con él
 * se responde 403 con un mensaje genérico, sin stack trace (SEC-W07), y queda
 * registrado en la auditoría.
 */
@Provider
public class AccesoDenegadoMapper implements ExceptionMapper<EJBAccessException> {
    @Context HttpServletRequest request;

    @Override
    public Response toResponse(EJBAccessException ex) {
        AuditoriaSeguridad.registrarUnaVez(request, "ACCESO_DENEGADO_ROL", "RECHAZADO",
                request == null ? "desconocido" : request.getRequestURI(), "HTTP_403");
        return Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("codigo", "ACCESO_DENEGADO",
                        "mensaje", "No tenés permisos para realizar esta operación",
                        "correlationId", AuditoriaSeguridad.correlationId(request)))
                .build();
    }
}