package com.stayhub.seguridad;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;

@WebFilter(urlPatterns = "/*")
public class SeguridadHttpFilter implements Filter {
    private final LimitadorSolicitudes limitador = new LimitadorSolicitudes();
    private final java.util.concurrent.Semaphore concurrentes = new java.util.concurrent.Semaphore(32);
    public static boolean httpLocalHabilitado() { return "true".equals(config("stayhub.seguridad.http-local")); }
    static String config(String clave) {
        String v = System.getProperty(clave);
        return v != null ? v : System.getenv(clave.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
    }
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        AuditoriaSeguridad.preparar(request, response);
        cabeceras(response, request.isSecure());
        String ruta = "/";
        boolean adquirido = false;
        try {
            try {
                ruta = URI.create(request.getRequestURI()).getPath().substring(request.getContextPath().length())
                        .replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
                if (ruta.contains("%") || Arrays.stream(ruta.split("/")).anyMatch(p -> p.equals(".") || p.equals("..")))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
                throw new SeguridadException(400, "Ruta inválida");
            }
            boolean api = ruta.startsWith("/api/"), soap = ruta.startsWith("/soap/");
            if (!Set.of("GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "DELETE").contains(request.getMethod()))
                throw new SeguridadException(405, "Método HTTP no permitido");
            if (request.getRequestURI().length() > 2048) throw new SeguridadException(414, "URI demasiado larga");
            int bytesHeaders = 0;
            for (var names = request.getHeaderNames(); names != null && names.hasMoreElements();) {
                String name = names.nextElement();
                for (var values = request.getHeaders(name); values != null && values.hasMoreElements();) {
                    bytesHeaders += name.length() + values.nextElement().length();
                    if (bytesHeaders > 16384) throw new SeguridadException(431, "Headers demasiado grandes");
                }
            }
            boolean local = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())
                    && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(request.getServerName());
            if (!request.isSecure() && !(httpLocalHabilitado() && local)) throw new SeguridadException(426, "Se requiere HTTPS");
            if (api || soap) {
                adquirido = concurrentes.tryAcquire();
                if (!adquirido) throw new SeguridadException(503, "El servicio está ocupado");
                cuotas(request, ruta);
                response.setHeader("Cache-Control", "no-store");
            }
            if (soap) autenticarPms(request);
            boolean mutacion = Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod());
            if (api && mutacion && !webhookValido(request, ruta)) {
                validarOrigen(request);
                if (!Csrf.valido(request.getSession(false), request.getHeader("X-CSRF-Token")))
                    throw new SeguridadException(403, "Token CSRF ausente o inválido");
            }
            if ((api || soap) && mutacion) {
                String encoding = request.getHeader("Content-Encoding");
                if (encoding != null && !"identity".equalsIgnoreCase(encoding)) throw new SeguridadException(415, "Contenido comprimido no permitido");
                if (request.getContentLengthLong() > CuerpoSeguro.MAX_BYTES) throw new SeguridadException(413, "El cuerpo supera 64 KiB");
                byte[] bytes = CuerpoSeguro.leer(request.getInputStream());
                if (bytes.length > 0) {
                    String tipo = Optional.ofNullable(request.getContentType()).orElse("").split(";")[0].trim();
                    if (soap) CuerpoSeguro.xml(bytes);
                    else if ("application/json".equalsIgnoreCase(tipo)) {
                        CuerpoSeguro.json(bytes);
                        if (ruta.equals("/api/usuarios/login")) cuotaCuenta(bytes);
                    }
                    else throw new SeguridadException(415, "Se requiere application/json");
                }
                request = new SolicitudConCuerpo(request, bytes);
            }
            chain.doFilter(request, response);
            AuditoriaSeguridad.auditarHttp(request, ruta, response.getStatus());
        } catch (SeguridadException ex) {
            AuditoriaSeguridad.registrar(request, "SOLICITUD_RECHAZADA", "RECHAZADO", ruta,
                    "HTTP_" + ex.getEstado());
            if (!response.isCommitted()) {
                response.setStatus(ex.getEstado()); response.setContentType("application/json;charset=UTF-8");
                if (ex.getEstado() == 429) response.setHeader("Retry-After", "60");
                response.getWriter().write(AuditoriaSeguridad.respuesta("SOLICITUD_RECHAZADA",
                        ex.getMessage(), AuditoriaSeguridad.correlationId(request)));
            }
        } catch (RuntimeException ex) {
            AuditoriaSeguridad.error(request, ex);
            if (response.isCommitted()) throw ex;
            response.setStatus(500); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(AuditoriaSeguridad.respuesta("ERROR_INTERNO",
                    "An unexpected error occurred.", AuditoriaSeguridad.correlationId(request)));
        } finally {
            if (adquirido) concurrentes.release();
        }
    }
    private void cuotaCuenta(byte[] bytes) {
        try (var reader = jakarta.json.Json.createReader(new java.io.ByteArrayInputStream(bytes))) {
            var json = reader.readObject();
            String email = json.getString("email", "").strip().toLowerCase(Locale.ROOT);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8)));
            if (!limitador.permitir("cuenta:" + hash, 10, System.currentTimeMillis())) throw new SeguridadException(429, "Demasiados intentos de acceso");
        } catch (SeguridadException ex) { throw ex; }
        catch (Exception ex) { throw new SeguridadException(400, "Credenciales inválidas"); }
    }
    private void cuotas(HttpServletRequest request, String ruta) {
        long ahora = System.currentTimeMillis(); String ip = request.getRemoteAddr();
        if (!limitador.permitir("global:" + ip, 180, ahora)) throw new SeguridadException(429, "Demasiadas solicitudes");
        String grupo = ruta.contains("login") ? "login" : ruta.equals("/api/usuarios") ? "registro" :
                ruta.contains("notificaciones") || ruta.contains("overbooking") ? "notificaciones" :
                ruta.contains("sincronizaciones") ? "sincronizacion" : "operacion";
        int limite = switch (grupo) { case "login" -> 10; case "registro" -> 5; case "notificaciones", "sincronizacion" -> 10; default -> 60; };
        if (!limitador.permitir(grupo + ":ip:" + ip, limite, ahora)) throw new SeguridadException(429, "Demasiadas solicitudes");
        HttpSession sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute("usuarioId");
        if (id != null && !limitador.permitir(grupo + ":usuario:" + id, limite, ahora)) throw new SeguridadException(429, "Demasiadas solicitudes");
    }
    private static void validarOrigen(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin == null) return; // El token sigue siendo obligatorio para clientes no navegador.
        try {
            URI uri = URI.create(origin);
            int puerto = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(req.getServerName())
                    || !req.getScheme().equals(uri.getScheme()) || puerto != req.getServerPort()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw new SeguridadException(403, "Origen no autorizado"); }
    }
    private static boolean webhookValido(HttpServletRequest req, String ruta) {
        if (!ruta.matches("/api/canales-externos/otas/[^/]+/reservas(?:/[^/]+)?")) return false;
        String esperada = config("stayhub.canales.api-key"), recibida = req.getHeader("X-StayHub-Channel-Key");
        return esperada != null && !esperada.isBlank() && recibida != null && recibida.length() < 512
                && MessageDigest.isEqual(esperada.getBytes(StandardCharsets.UTF_8), recibida.getBytes(StandardCharsets.UTF_8));
    }
    private static void autenticarPms(HttpServletRequest request) {
        // Atributo establecido por TLS del contenedor. Nunca se confía en un header de certificado.
        Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
        String huella = config("stayhub.pms.certificado-sha256"), ids = config("stayhub.pms.hoteles");
        if (!request.isSecure() || !(value instanceof X509Certificate[] certs) || certs.length == 0 || huella == null || ids == null)
            throw new SeguridadException(401, "Certificado PMS requerido");
        try {
            certs[0].checkValidity();
            byte[] esperado = HexFormat.of().parseHex(huella.replace(":", ""));
            if (esperado.length != 32 || !MessageDigest.isEqual(esperado, MessageDigest.getInstance("SHA-256").digest(certs[0].getEncoded())))
                throw new IllegalArgumentException();
            Set<Long> hoteles = new HashSet<>();
            for (String id : ids.split(",")) { Long hotel = Long.valueOf(id.trim()); if (hotel <= 0) throw new IllegalArgumentException(); hoteles.add(hotel); }
            if (hoteles.isEmpty() || hoteles.size() > 100) throw new IllegalArgumentException();
            request.setAttribute(IdentidadActual.PMS_HOTELES, Set.copyOf(hoteles));
        } catch (Exception ex) { throw new SeguridadException(403, "Certificado o permisos PMS inválidos"); }
    }
    private static void cabeceras(HttpServletResponse res, boolean tls) {
        res.setHeader("X-Content-Type-Options", "nosniff"); res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        res.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' https://lh3.googleusercontent.com data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'" + (tls ? "; upgrade-insecure-requests" : ""));
        if (tls) res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }
}
