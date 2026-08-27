package com.stayhub.usuarios.api;

import com.stayhub.usuarios.exception.UsuarioException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;

@Provider
public class UsuarioExceptionMapper implements ExceptionMapper<UsuarioException> {
    public Response toResponse(UsuarioException ex) {
        int estado = switch (ex.getCodigo()) {
            case SOLICITUD_INVALIDA -> 400;
            case USUARIO_NO_ENCONTRADO -> 404;
            case EMAIL_YA_REGISTRADO -> 409;
            case CREDENCIALES_INVALIDAS -> 401;
        };
        return Response.status(estado)
                .entity(new ErrorDTO(ex.getCodigo().name(), ex.getMessage(), OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}