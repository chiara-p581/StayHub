package com.stayhub.reservas.api;

import com.stayhub.reservas.exception.ReservaException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;

/** Mismo patrón que CanalExternoExceptionMapper, para mantener consistencia en el proyecto. */
@Provider
public class ReservaExceptionMapper implements ExceptionMapper<ReservaException> {
    public Response toResponse(ReservaException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA -> 400;
            case RESERVA_NO_ENCONTRADA -> 404;
            case RESERVA_DUPLICADA, SIN_DISPONIBILIDAD, TRANSICION_DE_ESTADO_INVALIDA -> 409;
            case DEPENDENCIA_NO_DISPONIBLE -> 503;
        };
        return Response.status(estado)
                .entity(new ErrorDTO(ex.getCodigo().name(), ex.getMessage(), OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
