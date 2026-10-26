package com.stayhub.seguridad;

import jakarta.annotation.Priority;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.container.ResourceInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AutenticacionFilter implements ContainerRequestFilter {

    private static final int MAX_CUERPO_WEBHOOK = 1_048_576;
    private static final long DURACION_ABSOLUTA_SESION_MS = 8L * 60 * 60 * 1000;
    private static final String INICIO_ABSOLUTO_SESION = "inicioAbsolutoSesion";
    private static final Pattern RUTA_WEBHOOK = Pattern.compile("canales-externos/otas/([^/]+)/reservas(?:/[^/]+)?");
    @jakarta.inject.Inject
    AutenticacionWebhookOta autenticacionWebhook;

    @Context
    HttpServletRequest request;
    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext contexto) {
        String ruta = normalizarRuta(contexto.getUriInfo().getPath());
        String metodo = contexto.getMethod();

        if ("OPTIONS".equals(metodo)) return;
        if (anotacion(PermitAll.class) != null) return;

        HttpSession sesion = request.getSession(false);
        if (sesion != null && sesionExpirada(sesion, System.currentTimeMillis())) {
            sesion.invalidate();
            abortar(contexto, Response.Status.UNAUTHORIZED, "SESION_EXPIRADA",
                    "La sesión expiró. Iniciá sesión nuevamente");
            return;
        }
        String rol = sesion == null ? null : (String) sesion.getAttribute("usuarioRol");

        if (anotacion(WebhookFirmado.class) != null) {
            if (firmaWebhookValida(contexto, ruta, metodo)) {
                AuditoriaSeguridad.registrar(request, "WEBHOOK_OTA_AUTENTICADO", "EXITO", ruta, "FIRMA_VALIDA");
                return;
            }
            abortar(contexto, Response.Status.UNAUTHORIZED, "FIRMA_OTA_INVALIDA",
                    "La firma del webhook no es válida o el mensaje ya fue procesado");
            return;
        }
        if (rol == null || sesion.getAttribute("usuarioId") == null) {
            abortar(contexto, Response.Status.UNAUTHORIZED, "NO_AUTENTICADO", "Iniciá sesión para continuar");
            return;
        }
        if (anotacion(DenyAll.class) != null) {
            abortar(contexto, Response.Status.FORBIDDEN, "ACCESO_DENEGADO", "Operación no permitida");
            return;
        }
        RolesAllowed roles = anotacion(RolesAllowed.class);
        if (roles != null && java.util.Arrays.stream(roles.value()).noneMatch(rol::equals)) {
            abortar(contexto, Response.Status.FORBIDDEN, "ACCESO_DENEGADO", "El rol no permite esta operación");
            return;
        }
        if ("ADMIN".equals(rol) && !Boolean.TRUE.equals(sesion.getAttribute(SesionSegura.MFA_VERIFICADA))) {
            abortar(contexto, Response.Status.UNAUTHORIZED, "MFA_REQUERIDO", "La sesión administrativa requiere MFA");
            return;
        }
        if (anotacion(ReautenticacionReciente.class) != null && !reauthReciente(sesion, System.currentTimeMillis())) {
            abortar(contexto, Response.Status.UNAUTHORIZED, "REAUTENTICACION_REQUERIDA",
                    "Volvé a confirmar contraseña y MFA para esta operación");
        }
    }

    boolean sesionExpirada(HttpSession sesion, long ahora) {
        return inicioSesionExpirado(sesion.getAttribute(INICIO_ABSOLUTO_SESION), ahora);
    }

    boolean inicioSesionExpirado(Object inicio, long ahora) {
        return !(inicio instanceof Long instante) || instante > ahora
                || ahora - instante > DURACION_ABSOLUTA_SESION_MS;
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

    boolean reauthReciente(HttpSession sesion, long ahora) {
        Object valor = sesion.getAttribute(SesionSegura.ADMIN_REAUTENTICADO_EN);
        return valor instanceof Long instante && instante <= ahora
                && ahora - instante <= SesionSegura.REAUTENTICACION_ADMIN_MS;
    }

    private <T extends java.lang.annotation.Annotation> T anotacion(Class<T> tipo) {
        if (resourceInfo == null) return null;
        java.lang.reflect.Method metodo = resourceInfo.getResourceMethod();
        if (metodo != null && metodo.isAnnotationPresent(tipo)) return metodo.getAnnotation(tipo);
        if (metodo != null && tipo != ReautenticacionReciente.class
                && (metodo.isAnnotationPresent(PermitAll.class)
                    || metodo.isAnnotationPresent(DenyAll.class)
                    || metodo.isAnnotationPresent(RolesAllowed.class)
                    || metodo.isAnnotationPresent(WebhookFirmado.class))) return null;
        Class<?> recurso = resourceInfo.getResourceClass();
        return recurso == null ? null : recurso.getAnnotation(tipo);
    }

    private boolean firmaWebhookValida(ContainerRequestContext contexto, String ruta, String metodo) {
        Matcher matcher = RUTA_WEBHOOK.matcher(ruta);
        if (!matcher.matches()) return false;
        try {
            byte[] cuerpo = contexto.getEntityStream().readNBytes(MAX_CUERPO_WEBHOOK + 1);
            contexto.setEntityStream(new ByteArrayInputStream(cuerpo));
            if (cuerpo.length > MAX_CUERPO_WEBHOOK) return false;
            if (autenticacionWebhook == null) autenticacionWebhook = new AutenticacionWebhookOta();
            return autenticacionWebhook.validar(matcher.group(1), metodo, ruta, cuerpo,
                    contexto.getHeaderString("X-StayHub-Timestamp"),
                    contexto.getHeaderString("X-StayHub-Nonce"),
                    contexto.getHeaderString("X-StayHub-Signature"));
        } catch (IOException ex) { return false; }
    }

    private void abortar(ContainerRequestContext contexto, Response.Status estado, String codigo, String mensaje) {
        AuditoriaSeguridad.registrar(request, "AUTORIZACION_RECHAZADA", "RECHAZADO",
                normalizarRuta(contexto.getUriInfo().getPath()), codigo);
        contexto.abortWith(Response.status(estado)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("codigo", codigo, "mensaje", mensaje))
                .build());
    }
}
