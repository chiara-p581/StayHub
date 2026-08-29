package com.stayhub.hoteles.api;

import com.stayhub.hoteles.exception.HotelException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.time.OffsetDateTime;

@Provider
public class HotelExceptionMapper implements ExceptionMapper<HotelException> {
    @Override public Response toResponse(HotelException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA -> 400;
            case HOTEL_NO_ENCONTRADO, TIPO_HABITACION_NO_ENCONTRADO, HABITACION_NO_ENCONTRADA -> 404;
            case CODIGO_DUPLICADO, NUMERO_HABITACION_DUPLICADO, HOTEL_INACTIVO, TIPO_HABITACION_EN_USO -> 409;
        };
        return Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(ex.getCodigo().name(), ex.getMessage(), OffsetDateTime.now())).build();
    }
}
