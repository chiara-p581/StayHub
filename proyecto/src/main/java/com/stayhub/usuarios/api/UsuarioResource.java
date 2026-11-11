package com.stayhub.usuarios.api;

import com.stayhub.seguridad.Csrf;
import com.stayhub.seguridad.IdentidadActual;
import com.stayhub.seguridad.SeguridadException;
import com.stayhub.seguridad.ProteccionLogin;
import com.stayhub.seguridad.MfaService;
import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.ActualizacionUsuarioRequest;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.ResetPasswordRequest;
import com.stayhub.usuarios.dto.SolicitudRecuperacionRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.model.RolUsuario;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsuarioResource {
    private static final String INICIO_ABSOLUTO_SESION = "inicioAbsolutoSesion";

    @Inject
    private ServicioDeUsuarios servicio;

    @Inject
    private ProteccionLogin proteccionLogin;

    @Inject
    private IdentidadActual identidad;
    @Inject private MfaService mfa;

    @POST
    public Response registrar(RegistroUsuarioRequest solicitud) {
        RegistroUsuarioRequest registroPublico = solicitud == null ? null : new RegistroUsuarioRequest(
                solicitud.email(), solicitud.password(), solicitud.nombre(), solicitud.apellido(), RolUsuario.HUESPED);
        UsuarioResponse usuario = servicio.registrar(registroPublico);
        return Response.status(Response.Status.CREATED)
                .location(URI.create("api/usuarios/" + usuario.id()))
                .entity(usuario)
                .build();
    }

    @POST
    @Path("/login")
    public UsuarioResponse login(LoginRequest credenciales, @Context HttpServletRequest request) {
        String email = credenciales == null ? null : credenciales.email();
        String ip = request.getRemoteAddr();
        proteccionLogin.verificar(email, ip);
        UsuarioResponse usuario;
        try {
            usuario = servicio.autenticar(credenciales);
        } catch (UsuarioException ex) {
            proteccionLogin.registrarFallo(email, ip);
            throw ex;
        }
        proteccionLogin.registrarExito(email, ip);
        boolean admin = "ADMIN".equals(usuario.rol());
        boolean mfaHabilitado = admin && mfa.habilitado(usuario.id());
        HttpSession anterior = request.getSession(false);
        if (anterior != null) anterior.invalidate();
        HttpSession sesion = request.getSession(true);
        request.changeSessionId();
        sesion.setMaxInactiveInterval(900);
        if (admin && mfaHabilitado) {
            sesion.setAttribute("mfaPendienteUsuarioId", usuario.id());
            sesion.setAttribute("mfaPendienteDesde", System.currentTimeMillis());
        } else {
            completarSesion(sesion, usuario, !admin);
        }
        Csrf.token(sesion);
        return admin ? new UsuarioResponse(usuario.id(), usuario.email(), usuario.nombre(), usuario.apellido(), usuario.rol(), true, mfaHabilitado) : usuario;
    }

    public record CodigoMfaRequest(String codigo) { }
    public record ReautenticacionRequest(String password, String codigoMfa) { }
    public record ActivacionMfaResponse(UsuarioResponse usuario, List<String> codigosRecuperacion) { }

    @POST @Path("/mfa/verificar-login")
    public UsuarioResponse verificarMfaLogin(CodigoMfaRequest body, @Context HttpServletRequest request) {
        HttpSession sesion=request.getSession(false);
        Object id=sesion==null?null:sesion.getAttribute("mfaPendienteUsuarioId");
        Object desde=sesion==null?null:sesion.getAttribute("mfaPendienteDesde");
        long ahora=System.currentTimeMillis();
        if(!(id instanceof Long usuarioId)||!(desde instanceof Long inicio)||inicio>ahora||ahora-inicio>300_000L
                ||body==null||!mfa.validarAcceso(usuarioId,body.codigo())) throw new SeguridadException(401,"Código MFA requerido o inválido");
        UsuarioResponse usuario=servicio.buscarPorId(usuarioId);
        completarSesion(sesion,usuario,true);
        sesion.removeAttribute("mfaPendienteUsuarioId");sesion.removeAttribute("mfaPendienteDesde");
        return usuario;
    }

    @GET @Path("/mfa/estado")
    public Map<String,Object> estadoMfa() {
        if (!identidad.admin()) throw new SeguridadException(403,"Sólo disponible para administradores");
        return Map.of("habilitado",mfa.habilitado(identidad.usuarioId()));
    }

    @POST @Path("/mfa/preparar")
    public Map<String,Object> prepararMfa() {
        if (!identidad.admin()) throw new SeguridadException(403,"Sólo disponible para administradores");
        return mfa.preparar(identidad.usuarioId(), servicio.buscarPorId(identidad.usuarioId()).email());
    }

    @POST @Path("/mfa/activar")
    public ActivacionMfaResponse activarMfa(CodigoMfaRequest body, @Context HttpServletRequest request) {
        if (!identidad.admin()) throw new SeguridadException(403,"Sólo disponible para administradores");
        List<String> codigos = mfa.activar(identidad.usuarioId(), body == null ? null : body.codigo());
        request.getSession(false).setAttribute("mfaVerificada", true);
        request.getSession(false).setAttribute("reautenticadoEn", System.currentTimeMillis());
        return new ActivacionMfaResponse(servicio.buscarPorId(identidad.usuarioId()), codigos);
    }

    @POST @Path("/mfa/codigos-recuperacion/regenerar")
    public Map<String,Object> regenerarCodigosRecuperacion() {
        if (!identidad.admin()) throw new SeguridadException(403,"Sólo disponible para administradores");
        return Map.of("codigosRecuperacion", mfa.regenerarCodigos(identidad.usuarioId()));
    }

    @POST @Path("/mfa/reiniciar")
    public Response reiniciarMfa(@Context HttpServletRequest request) {
        if (!identidad.admin()) throw new SeguridadException(403,"Sólo disponible para administradores");
        mfa.reiniciar(identidad.usuarioId());
        HttpSession sesion = request.getSession(false);
        sesion.setAttribute("mfaVerificada", false);
        sesion.removeAttribute("reautenticadoEn");
        return Response.noContent().build();
    }

    @POST @Path("/reautenticar")
    public Response reautenticar(ReautenticacionRequest body, @Context HttpServletRequest request) {
        if (!identidad.admin() || body == null) throw new SeguridadException(403,"Reautenticación administrativa requerida");
        UsuarioResponse actual = servicio.buscarPorId(identidad.usuarioId());
        servicio.autenticar(new LoginRequest(actual.email(), body.password()));
        if (!mfa.habilitado(actual.id()) || !mfa.validarAcceso(actual.id(), body.codigoMfa()))
            throw new SeguridadException(401,"Código MFA requerido o inválido");
        request.getSession(false).setAttribute("reautenticadoEn", System.currentTimeMillis());
        return Response.noContent().build();
    }

    private void completarSesion(HttpSession sesion, UsuarioResponse usuario, boolean mfaVerificada) {
        sesion.setAttribute("usuarioId",usuario.id());sesion.setAttribute("usuarioRol",usuario.rol());
        sesion.setAttribute(INICIO_ABSOLUTO_SESION,System.currentTimeMillis());
        sesion.setAttribute("mfaVerificada",mfaVerificada);
        if(mfaVerificada)sesion.setAttribute("reautenticadoEn",System.currentTimeMillis());
    }

    @DELETE
    @Path("/sesion")
    public Response cerrarSesion(@Context HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) sesion.invalidate();
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}")
    public UsuarioResponse consultar(@PathParam("id") Long id) {
        // SEC-W06 (BOLA): el perfil de otra persona responde 404, igual que un id
        // inexistente, para no confirmar qué usuarios existen. La identidad sale de
        // la sesión (IdentidadActual), nunca de datos que mande el cliente.
        Long usuarioId = identidad.usuarioId();
        if (!usuarioId.equals(id) && !identidad.admin()) {
            throw new SeguridadException(404, "Usuario no disponible");
        }
        return servicio.buscarPorId(id);
    }

    @PUT
    @Path("/me")
    public UsuarioResponse actualizarMiPerfil(ActualizacionUsuarioRequest solicitud,
                                              @Context HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null || sesion.getAttribute("usuarioId") == null) {
            throw new WebApplicationException("Iniciá sesión para continuar", Response.Status.UNAUTHORIZED);
        }
        UsuarioResponse actualizado = servicio.actualizarPerfil((Long) sesion.getAttribute("usuarioId"), solicitud);
        sesion.setAttribute("usuarioRol", actualizado.rol());
        return actualizado;
    }

    // ---- Recuperación de contraseña (públicos: se usan sin estar logueado) ----

    @POST
    @Path("/recuperar-password")
    public Response recuperar(SolicitudRecuperacionRequest s) {
        if (s == null) throw new BadRequestException("Falta la solicitud");
        servicio.solicitarRecuperacionPassword(s.email());
        // Misma respuesta exista o no la cuenta
        return Response.accepted(Map.of("mensaje",
                "Si existe la cuenta, recibirás un enlace de recuperación")).build();
    }

    @POST
    @Path("/resetear-password")
    public Response resetear(ResetPasswordRequest s) {
        if (s == null) throw new BadRequestException("Falta la solicitud");
        servicio.resetearPassword(s.token(), s.nuevaPassword());
        return Response.noContent().build();
    }
}
