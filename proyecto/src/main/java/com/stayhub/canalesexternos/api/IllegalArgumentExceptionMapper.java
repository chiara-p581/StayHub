package com.stayhub.canalesexternos.api;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.time.OffsetDateTime;
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    public Response toResponse(IllegalArgumentException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorDTO("SOLICITUD_INVALIDA",ex.getMessage(),OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
