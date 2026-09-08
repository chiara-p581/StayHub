package com.stayhub.hoteles.api;

import jakarta.json.JsonException;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;

/** Impide que los errores de deserialización expongan clases o mensajes internos de JSON-B. */
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<ProcessingException> {
    @Override
    public Response toResponse(ProcessingException ex) {
        boolean errorDeJson = esErrorDeJson(ex);
        int estado = errorDeJson ? 400 : 500;
        String codigo = errorDeJson ? "SOLICITUD_INVALIDA" : "ERROR_INTERNO";
        String mensaje = errorDeJson
                ? "El cuerpo de la solicitud debe ser un JSON válido y respetar los tipos esperados"
                : "No se pudo procesar la solicitud";
        return Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(codigo, mensaje, OffsetDateTime.now())).build();
    }

    static boolean esErrorDeJson(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof JsonbException || causa instanceof JsonException) return true;
        }
        return false;
    }
}
