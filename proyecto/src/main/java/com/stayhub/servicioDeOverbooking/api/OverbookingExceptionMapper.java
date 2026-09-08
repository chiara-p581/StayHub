package com.stayhub.servicioDeOverbooking.api;

import com.stayhub.servicioDeOverbooking.exception.OverbookingException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.time.OffsetDateTime;

@Provider
public class OverbookingExceptionMapper implements ExceptionMapper<OverbookingException> {
    public Response toResponse(OverbookingException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA -> 400;
            case DEPENDENCIA_NO_DISPONIBLE -> 503;
            default -> 502;
        };
        return Response.status(estado)
                .entity(new ErrorDTO(ex.getCodigo().name(), ex.getMessage(), OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}