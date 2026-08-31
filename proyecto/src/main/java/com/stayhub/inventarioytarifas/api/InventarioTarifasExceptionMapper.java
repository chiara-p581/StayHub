package com.stayhub.inventarioytarifas.api;

import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;

@Provider
public class InventarioTarifasExceptionMapper implements ExceptionMapper<InventarioTarifasException> {
    public Response toResponse(InventarioTarifasException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA -> 400;
            case HOLD_NO_ENCONTRADO -> 404;
            case TRANSICION_DE_ESTADO_INVALIDA -> 409;
            case SOBREVENTA_DETECTADA -> 409;
        };
        return Response.status(estado)
                .entity(new ErrorDTO(ex.getCodigo().name(), ex.getMessage(), OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
