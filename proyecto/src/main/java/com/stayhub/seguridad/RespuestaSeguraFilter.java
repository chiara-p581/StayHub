package com.stayhub.seguridad;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Política única para toda respuesta REST de error, incluso las creadas por mappers de componentes. */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class RespuestaSeguraFilter implements ContainerResponseFilter {
    static final String PROBLEM_JSON = "application/problem+json";
    @Context HttpServletRequest request;

    @Override
    public void filter(ContainerRequestContext solicitud, ContainerResponseContext respuesta) throws IOException {
        String correlacion = AuditoriaSeguridad.correlationId(request);
        respuesta.getHeaders().putSingle(AuditoriaSeguridad.HEADER_CORRELACION, correlacion);
        respuesta.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        if (respuesta.getStatus() < 400) return;

        respuesta.getHeaders().putSingle("Cache-Control", "no-store");
        String ruta = solicitud.getUriInfo().getPath();
        AuditoriaSeguridad.registrarUnaVez(request, "RESPUESTA_ERROR", "RECHAZADO",
                ruta, "HTTP_" + respuesta.getStatus());

        respuesta.setEntity(problema(respuesta.getStatus(), respuesta.getEntity(), ruta, correlacion));
        respuesta.getHeaders().putSingle("Content-Type", PROBLEM_JSON);
    }

    static Map<String, Object> problema(int estado, Object entidad, String ruta, String correlacion) {
        boolean interno = estado >= 500;
        String codigo = interno ? "ERROR_INTERNO" : codigoSeguro(extraer(entidad, "codigo"), estado);
        String detalle = interno
                ? "Ocurrió un error interno. Intentá nuevamente más tarde."
                : textoSeguro(extraerMensaje(entidad), detallePredeterminado(estado));

        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("type", "urn:stayhub:problem:" + codigo.toLowerCase().replace('_', '-'));
        problema.put("title", titulo(estado));
        problema.put("status", estado);
        problema.put("detail", detalle);
        problema.put("instance", ruta == null || ruta.isBlank() ? "/" : "/" + ruta.replaceFirst("^/+", ""));
        problema.put("codigo", codigo);
        problema.put("mensaje", detalle); // compatibilidad con clientes existentes
        problema.put("correlationId", correlacion == null ? "sin-correlacion" : correlacion);
        problema.put("timestamp", OffsetDateTime.now().toString());
        return problema;
    }

    private static String extraerMensaje(Object entidad) {
        String mensaje = extraer(entidad, "mensaje");
        if (mensaje == null) mensaje = extraer(entidad, "detail");
        return mensaje;
    }

    private static String extraer(Object entidad, String campo) {
        if (entidad instanceof Map<?, ?> mapa) {
            Object valor = mapa.get(campo);
            return valor instanceof String texto ? texto : null;
        }
        if (entidad == null) return null;
        try {
            Method metodo = entidad.getClass().getMethod(campo);
            Object valor = metodo.invoke(entidad);
            return valor instanceof String texto ? texto : null;
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }

    private static String codigoSeguro(String codigo, int estado) {
        return codigo != null && codigo.matches("[A-Z][A-Z0-9_]{0,63}") ? codigo : "HTTP_" + estado;
    }

    private static String textoSeguro(String texto, String predeterminado) {
        if (texto == null || texto.isBlank()) return predeterminado;
        String limpio = texto.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        return limpio.length() <= 500 ? limpio : limpio.substring(0, 500);
    }

    static String titulo(int estado) {
        return switch (estado) {
            case 400 -> "Solicitud inválida";
            case 401 -> "No autenticado";
            case 403 -> "Acceso denegado";
            case 404 -> "Recurso no encontrado";
            case 409 -> "Conflicto";
            case 422 -> "Entidad no procesable";
            case 429 -> "Demasiadas solicitudes";
            case 500 -> "Error interno";
            case 502 -> "Respuesta externa inválida";
            case 503 -> "Servicio no disponible";
            case 504 -> "Tiempo de espera agotado";
            default -> estado >= 500 ? "Error del servidor" : "Error de solicitud";
        };
    }

    static String detallePredeterminado(int estado) {
        return switch (estado) {
            case 400 -> "La solicitud no tiene un formato válido.";
            case 401 -> "Iniciá sesión para continuar.";
            case 403 -> "No tenés permisos para realizar esta operación.";
            case 404 -> "El recurso solicitado no existe.";
            case 409 -> "La operación entra en conflicto con el estado actual del recurso.";
            case 422 -> "La solicitud es válida, pero no puede procesarse.";
            case 429 -> "Se superó temporalmente el límite de solicitudes.";
            default -> "La solicitud no pudo completarse.";
        };
    }
}
