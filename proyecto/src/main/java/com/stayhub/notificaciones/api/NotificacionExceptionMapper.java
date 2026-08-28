package com.stayhub.notificaciones.api;

import com.stayhub.notificaciones.exception.NotificacionException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.time.OffsetDateTime;

@Provider
public class NotificacionExceptionMapper implements ExceptionMapper<NotificacionException> {
    public Response toResponse(NotificacionException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA, CANAL_NO_SOPORTADO -> 400;
            default -> 502;
        };
        return Response.status(estado)
                .entity(new ErrorDTO(ex.getCodigo().name(), ex.getMessage(), OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}