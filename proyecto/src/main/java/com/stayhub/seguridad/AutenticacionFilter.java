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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AutenticacionFilter implements ContainerRequestFilter {

    private static final String HEADER_CANAL = "X-StayHub-Channel-Key";

    @Context
    HttpServletRequest request;

    @Override
    public void filter(ContainerRequestContext contexto) {
        String ruta = normalizarRuta(contexto.getUriInfo().getPath());
        String metodo = contexto.getMethod();

        if ("OPTIONS".equals(metodo) || esPublica(ruta, metodo)) return;

        HttpSession sesion = request.getSession(false);
        String rol = sesion == null ? null : (String) sesion.getAttribute("usuarioRol");

        if (esWebhookOta(ruta) && ("ADMIN".equals(rol) || claveCanalValida(contexto))) return;
        if (rol == null) {
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
        String normalizada = ruta.trim().replace('\\', '/');
        while (normalizada.startsWith("/")) normalizada = normalizada.substring(1);
        if (normalizada.equals("api")) return "";
        if (normalizada.startsWith("api/")) normalizada = normalizada.substring(4);
        while (normalizada.endsWith("/") && !normalizada.isEmpty()) {
            normalizada = normalizada.substring(0, normalizada.length() - 1);
        }
        return normalizada;
    }

    boolean esPublica(String ruta, String metodo) {
        if ("POST".equals(metodo) && ("usuarios".equals(ruta) || "usuarios/login".equals(ruta))) return true;
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

    private boolean claveCanalValida(ContainerRequestContext contexto) {
        String esperada = System.getProperty("stayhub.canales.api-key");
        if (esperada == null || esperada.isBlank()) esperada = System.getenv("STAYHUB_CANALES_API_KEY");
        String recibida = contexto.getHeaderString(HEADER_CANAL);
        return esperada != null && recibida != null && MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8), recibida.getBytes(StandardCharsets.UTF_8));
    }

    private void abortar(ContainerRequestContext contexto, Response.Status estado, String codigo, String mensaje) {
        contexto.abortWith(Response.status(estado)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("codigo", codigo, "mensaje", mensaje))
                .build());
    }
}
