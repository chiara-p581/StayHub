package com.stayhub.reservas.api;

import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.service.ServicioDeReservas;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

    @POST
    public Response crear(ReservaRequest solicitud) {
        ReservaResponse creada = servicio.crearReserva(solicitud);
        return Response.created(URI.create("api/reservas/" + creada.id())).entity(creada).build();
    }

    @GET
    @Path("/{id}")
    public ReservaResponse consultar(@PathParam("id") Long id) {
        return servicio.consultarReserva(id);
    }

    @POST
    @Path("/{id}/confirmacion")
    public ReservaResponse confirmar(@PathParam("id") Long id) {
        return servicio.confirmarReserva(id);
    }

    @DELETE
    @Path("/{id}")
    public ReservaResponse cancelar(@PathParam("id") Long id) {
        return servicio.cancelarReserva(id);
    }

    @GET
    public List<ReservaResponse> listarPorHotel(@QueryParam("hotelId") Long hotelId) {
        return servicio.listarPorHotel(hotelId);
    }
}
