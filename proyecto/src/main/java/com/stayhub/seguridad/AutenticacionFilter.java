package com.stayhub.seguridad;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AutenticacionFilter implements ContainerRequestFilter {

    private static final int MAX_CUERPO_WEBHOOK = 1_048_576;
    private static final Pattern RUTA_WEBHOOK = Pattern.compile("canales-externos/otas/([^/]+)/reservas(?:/[^/]+)?");
    private final AutenticacionWebhookOta autenticacionWebhook = new AutenticacionWebhookOta();
    private final LimitadorLogin limitadorLogin = new LimitadorLogin();

    @Context
    HttpServletRequest request;

    @Override
    public void filter(ContainerRequestContext contexto) {
        String ruta = normalizarRuta(contexto.getUriInfo().getPath());
        String metodo = contexto.getMethod();

        if ("OPTIONS".equals(metodo)) return;
        if ("POST".equals(metodo) && "usuarios/login".equals(ruta)
                && !limitadorLogin.permitir(request.getRemoteAddr())) {
            abortar(contexto, Response.Status.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS",
                    "Too many login attempts. Please try again in 10 minutes.");
            return;
        }
        if (esPublica(ruta, metodo)) return;

        HttpSession sesion = request.getSession(false);
        String rol = sesion == null ? null : (String) sesion.getAttribute("usuarioRol");

        if (esWebhookOta(ruta)) {
            if (firmaWebhookValida(contexto, ruta, metodo)) return;
            abortar(contexto, Response.Status.UNAUTHORIZED, "FIRMA_OTA_INVALIDA",
                    "La firma del webhook no es válida o el mensaje ya fue procesado");
            return;
        }
        if (rol == null || sesion.getAttribute("usuarioId") == null) {
            abortar(contexto, Response.Status.UNAUTHORIZED, "NO_AUTENTICADO", "Iniciá sesión para continuar");
            return;
        }
        if ("ADMIN".equals(rol) && esCompra(ruta, metodo)) {
            abortar(contexto, Response.Status.FORBIDDEN, "ADMIN_NO_COMPRA",
                    "Las cuentas ADMIN administran StayHub y no pueden realizar reservas ni compras");
            return;
        }
        if (requiereAdmin(ruta, metodo) && !"ADMIN".equals(rol)) {
            abortar(contexto, Response.Status.FORBIDDEN, "ACCESO_DENEGADO", "Esta operación requiere rol ADMIN");
        }
    }

    String normalizarRuta(String ruta) {
        if (ruta == null) return "";
        String normalizada = ruta.trim().replace('\\', '/').replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
        while (normalizada.startsWith("/")) normalizada = normalizada.substring(1);
        if (normalizada.equals("api")) return "";
        if (normalizada.startsWith("api/")) normalizada = normalizada.substring(4);
        while (normalizada.endsWith("/") && !normalizada.isEmpty()) {
            normalizada = normalizada.substring(0, normalizada.length() - 1);
        }
        return normalizada;
    }

    boolean esPublica(String ruta, String metodo) {
        // Registro, login y recuperación de contraseña: se usan sin sesión iniciada
        if ("POST".equals(metodo) && ("usuarios".equals(ruta) || "usuarios/login".equals(ruta)
                || "usuarios/recuperar-password".equals(ruta) || "usuarios/resetear-password".equals(ruta))) return true;
        if ("GET".equals(metodo) && ruta.equals("seguridad/csrf")) return true;
        if ("GET".equals(metodo) && (ruta.equals("hoteles") || ruta.startsWith("hoteles/"))) return true;
        if ("GET".equals(metodo) && ruta.startsWith("catalogo-canales/")) return true;
        return "GET".equals(metodo) && ruta.startsWith("inventario-tarifas/");
    }

    boolean esCompra(String ruta, String metodo) {
        return !"GET".equals(metodo) && (ruta.startsWith("carrito")
                || ruta.equals("reservas") || ruta.startsWith("pagos"));
    }

    boolean requiereAdmin(String ruta, String metodo) {
        return ruta.startsWith("canales-externos/")
                || ruta.startsWith("demo/")
                || ruta.startsWith("overbooking/")
                || ruta.startsWith("notificaciones/")
                || (!"GET".equals(metodo) && ruta.startsWith("hoteles"))
                || (!"GET".equals(metodo) && ruta.startsWith("inventario-tarifas/"))
                || ("GET".equals(metodo) && "reservas".equals(ruta));
    }

    boolean esWebhookOta(String ruta) {
        return ruta.matches("canales-externos/otas/[^/]+/reservas(?:/[^/]+)?");
    }

    private boolean firmaWebhookValida(ContainerRequestContext contexto, String ruta, String metodo) {
        Matcher matcher = RUTA_WEBHOOK.matcher(ruta);
        if (!matcher.matches()) return false;
        try {
            byte[] cuerpo = contexto.getEntityStream().readNBytes(MAX_CUERPO_WEBHOOK + 1);
            contexto.setEntityStream(new ByteArrayInputStream(cuerpo));
            if (cuerpo.length > MAX_CUERPO_WEBHOOK) return false;
            return autenticacionWebhook.validar(matcher.group(1), metodo, ruta, cuerpo,
                    contexto.getHeaderString("X-StayHub-Timestamp"),
                    contexto.getHeaderString("X-StayHub-Nonce"),
                    contexto.getHeaderString("X-StayHub-Signature"));
        } catch (IOException ex) { return false; }
    }

    private void abortar(ContainerRequestContext contexto, Response.Status estado, String codigo, String mensaje) {
        contexto.abortWith(Response.status(estado)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("codigo", codigo, "mensaje", mensaje))
                .build());
    }
}
