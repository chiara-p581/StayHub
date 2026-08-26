package com.stayhub.canalesexternos.api;
import com.stayhub.canalesexternos.exception.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.time.OffsetDateTime;
@Provider
public class CanalExternoExceptionMapper implements ExceptionMapper<CanalExternoException> {
    public Response toResponse(CanalExternoException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA, CANAL_NO_SOPORTADO -> 400;
            case DEPENDENCIA_NO_DISPONIBLE -> 503;
            default -> 502;
        };
        return Response.status(estado).entity(new ErrorDTO(ex.getCodigo().name(),ex.getMessage(),OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
