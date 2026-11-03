package com.stayhub.reservas.api;

import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.service.ServicioDeReservas;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

/**
 * Capa de presentación para las reservas hechas directamente en StayHub
 * (no confundir con los endpoints /canales-externos/otas/..., esos son de
 * ServicioDeCanalesExternos). Sigue el mismo estilo JAX-RS que
 * CanalExternoResource, para mantener consistencia dentro del proyecto.
 *
 * Base: /StayHub/api/reservas
 */
@Path("/reservas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReservaResource {

    @Inject
    private ServicioDeReservas servicio;


    @Context
    private UriInfo uriInfo;

    @Context
    private HttpServletRequest request;

    @GET
    @Path("/mias")
    public List<ReservaResponse> listarMias(@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("limite") @DefaultValue("100") int limite) {
        return servicio.listarMias(offset, limite);
    }

    @POST
    public Response crear(ReservaRequest solicitud) {
        ReservaResponse creada = servicio.crearReserva(solicitud);
        // UriInfo.getAbsolutePathBuilder() ya apunta a la URL completa de este
        // POST (con esquema, host y puerto reales); antes se armaba a mano
        // como "api/reservas/<id>", una URL relativa que no sigue el estándar
        // REST (el header Location debería ser una URL absoluta).
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(creada.id())).build();
        return Response.created(location).entity(creada).build();
    }

    /** Devuelve la reserva con su versión en el header ETag (para usar luego en If-Match). */
    @GET
    @Path("/{id}")
    public Response consultar(@PathParam("id") Long id) {
        ReservaResponse reserva = servicio.consultarReserva(id);
        return Response.ok(reserva).header(HttpHeaders.ETAG, ETagReserva.formatear(reserva.version())).build();
    }

    @POST
    @Path("/{id}/confirmacion")
    public ReservaResponse confirmar(@PathParam("id") Long id) {
        return servicio.confirmarReserva(id);
    }

    /**
     * Modificación con control de concurrencia: exige If-Match con el ETag
     * leído. ETag vigente -> 200 con el nuevo ETag; ETag obsoleto -> 412;
     * sin If-Match -> 428.
     */
    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") Long id,
                              @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
                              ReservaRequest solicitud) {
        ReservaResponse modificada = servicio.modificarReserva(id, solicitud, ETagReserva.versionDesdeIfMatch(ifMatch));
        return Response.ok(modificada).header(HttpHeaders.ETAG, ETagReserva.formatear(modificada.version())).build();
    }

    @DELETE
    @Path("/{id}")
    public ReservaResponse cancelar(@PathParam("id") Long id) {
        return servicio.cancelarReserva(id);
    }

    @GET
    public List<ReservaResponse> listarPorHotel(@QueryParam("hotelId") Long hotelId, @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("limite") @DefaultValue("100") int limite) {
        return servicio.listarPorHotel(hotelId, offset, limite);
    }
}
