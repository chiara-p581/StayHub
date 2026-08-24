package com.stayhub.pagos.api;

import com.stayhub.pagos.exception.PagoException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;

@Provider
public class PagoExceptionMapper implements ExceptionMapper<PagoException> {
    public Response toResponse(PagoException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA -> 400;
            case PAGO_NO_ENCONTRADO -> 404;
            case PAGO_RECHAZADO -> 402;
            case ERROR_COMUNICACION_PASARELA -> 502;
        };
        return Response.status(estado)
                .entity(new ErrorDTO(ex.getCodigo().name(), ex.getMessage(), OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}