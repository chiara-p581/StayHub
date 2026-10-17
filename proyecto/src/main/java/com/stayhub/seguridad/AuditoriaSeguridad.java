package com.stayhub.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Registro estructurado y saneado para decisiones de seguridad y operaciones sensibles. */
public final class AuditoriaSeguridad {
    public static final String HEADER_CORRELACION = "X-Correlation-ID";
    public static final String ATRIBUTO_CORRELACION = AuditoriaSeguridad.class.getName() + ".correlationId";
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern VALOR_SEGURO = Pattern.compile("[A-Za-z0-9áéíóúÁÉÍÓÚñÑ._:/@ -]{0,160}");
    private static final Logger AUDIT = Logger.getLogger("stayhub.security.audit");
    private static final Logger ERROR = Logger.getLogger("stayhub.error");

    private AuditoriaSeguridad() {}

    public static String preparar(HttpServletRequest request, HttpServletResponse response) {
        String recibido = request.getHeader(HEADER_CORRELACION);
        String id = recibido != null && ID_VALIDO.matcher(recibido).matches()
                ? recibido : UUID.randomUUID().toString();
        request.setAttribute(ATRIBUTO_CORRELACION, id);
        response.setHeader(HEADER_CORRELACION, id);
        return id;
    }

    public static String correlationId(HttpServletRequest request) {
        Object valor = request == null ? null : request.getAttribute(ATRIBUTO_CORRELACION);
        return valor instanceof String id && ID_VALIDO.matcher(id).matches() ? id : "sin-correlacion";
    }

    public static void registrar(HttpServletRequest request, String accion, String resultado,
                                 String recurso, String detalle) {
        HttpSession sesion = request == null ? null : request.getSession(false);
        Object actor = sesion == null ? null : sesion.getAttribute("usuarioId");
        Object rol = sesion == null ? null : sesion.getAttribute("usuarioRol");
        String origen = request == null ? "desconocido" : huella(request.getRemoteAddr());
        AUDIT.info(() -> "AUDIT " + json(
                "fecha", Instant.now().toString(),
                "correlationId", correlationId(request),
                "actorId", actor == null ? "anonimo" : String.valueOf(actor),
                "rol", rol == null ? "ANONIMO" : String.valueOf(rol),
                "accion", accion,
                "resultado", resultado,
                "recurso", recurso,
                "origenHash", origen,
                "detalle", detalle));
    }

    public static void error(HttpServletRequest request, Throwable error) {
        String tipo = error == null ? "desconocido" : error.getClass().getName();
        String ubicacion = "sin-ubicacion";
        if (error != null && error.getStackTrace().length > 0) {
            StackTraceElement p = error.getStackTrace()[0];
            ubicacion = p.getClassName() + "." + p.getMethodName() + ":" + p.getLineNumber();
        }
        String linea = "ERROR " + json("fecha", Instant.now().toString(),
                "correlationId", correlationId(request), "tipo", tipo, "ubicacion", ubicacion);
        ERROR.log(Level.SEVERE, linea); // No registra mensajes, cuerpos, credenciales ni stack traces con PII.
    }

    public static void auditarHttp(HttpServletRequest request, String ruta, int estado) {
        String metodo = request.getMethod();
        if (!java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(metodo)) return;
        String accion;
        if (ruta.equals("/api/usuarios/login")) accion = estado < 400 ? "LOGIN_EXITOSO" : "LOGIN_FALLIDO";
        else if (ruta.equals("/api/usuarios/sesion")) accion = "LOGOUT";
        else if (ruta.equals("/api/usuarios/me")) accion = "PERFIL_MODIFICADO";
        else if (ruta.startsWith("/api/pagos")) accion = "PAGO_PROCESADO";
        else if (ruta.startsWith("/api/reservas") || ruta.startsWith("/api/carrito")) accion = "RESERVA_MUTADA";
        else if (ruta.matches("/api/canales-externos/otas/[^/]+/reservas(?:/[^/]+)?")) accion = "WEBHOOK_OTA_PROCESADO";
        else {
            HttpSession s = request.getSession(false);
            accion = s != null && "ADMIN".equals(s.getAttribute("usuarioRol"))
                    ? "OPERACION_ADMINISTRATIVA" : "OPERACION_MUTABLE";
        }
        registrar(request, accion, estado < 400 ? "EXITO" : "RECHAZADO", ruta, "HTTP_" + estado);
    }

    public static String respuesta(String codigo, String mensaje, String correlationId) {
        return json("codigo", codigo, "mensaje", mensaje, "correlationId", correlationId);
    }

    private static String huella(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(valor).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception ex) { return "no-disponible"; }
    }

    private static String json(String... pares) {
        StringBuilder salida = new StringBuilder("{");
        for (int i = 0; i < pares.length; i += 2) {
            if (i > 0) salida.append(',');
            salida.append('"').append(escape(pares[i])).append("\":\"")
                    .append(escape(sanitizar(pares[i + 1]))).append('"');
        }
        return salida.append('}').toString();
    }

    private static String sanitizar(String valor) {
        String limpio = String.valueOf(valor).replaceAll("[\\r\\n\\t]", " ");
        if (limpio.length() > 160) limpio = limpio.substring(0, 160);
        return VALOR_SEGURO.matcher(limpio).matches() ? limpio : limpio.replaceAll("[^A-Za-z0-9._:/@ -]", "?");
    }

    private static String escape(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
