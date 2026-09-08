package com.stayhub.hoteles.api;

import com.stayhub.hoteles.dto.*;
import com.stayhub.hoteles.service.ServicioDeHoteles;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.List;

/**
 * Capa de presentación REST de ServicioDeHoteles. Solo adapta HTTP a llamadas de la Facade y no
 * contiene reglas de negocio ni acceso directo a datos.
 */
@Path("/hoteles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HotelResource {
    @Inject
    private ServicioDeHoteles servicio;

    @POST
    public Response crear(HotelRequest solicitud, @Context UriInfo uriInfo) {
        HotelResponse creado = servicio.crearHotel(solicitud);
        URI ubicacion = uriInfo.getAbsolutePathBuilder().path(creado.id().toString()).build();
        return Response.created(ubicacion).entity(creado).build();
    }
    @GET
    public List<HotelResponse> listar(
            @QueryParam("incluirInactivos") @DefaultValue("false") boolean incluir) {
        return servicio.listarHoteles(incluir);
    }

    @GET
    @Path("/{id}")
    public HotelResponse consultar(@PathParam("id") Long id) {
        return servicio.consultarHotel(id);
    }

    @PUT
    @Path("/{id}")
    public HotelResponse modificar(@PathParam("id") Long id, HotelRequest solicitud) {
        return servicio.modificarHotel(id, solicitud);
    }

    @DELETE
    @Path("/{id}")
    public HotelResponse eliminar(@PathParam("id") Long id) {
        return servicio.darDeBajaHotel(id);
    }

    @POST
    @Path("/{hotelId}/tipos-habitacion")
    public Response crearTipo(@PathParam("hotelId") Long hotelId, TipoHabitacionRequest solicitud,
                              @Context UriInfo uriInfo) {
        TipoHabitacionResponse creado = servicio.crearTipo(hotelId, solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(creado.id().toString()).build())
                .entity(creado).build();
    }

    @GET
    @Path("/{hotelId}/tipos-habitacion")
    public List<TipoHabitacionResponse> listarTipos(
            @PathParam("hotelId") Long hotelId,
            @QueryParam("incluirInactivos") @DefaultValue("false") boolean incluirInactivos) {
        return servicio.listarTipos(hotelId, incluirInactivos);
    }

    @GET
    @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public TipoHabitacionResponse consultarTipo(
            @PathParam("hotelId") Long hotelId,
            @PathParam("tipoId") Long tipoId) {
        return servicio.consultarTipo(hotelId, tipoId);
    }

    @PUT
    @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public TipoHabitacionResponse modificarTipo(@PathParam("hotelId") Long hotelId,
            @PathParam("tipoId") Long tipoId, TipoHabitacionRequest solicitud) {
        return servicio.modificarTipo(hotelId, tipoId, solicitud);
    }

    @DELETE
    @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public Response eliminarTipo(@PathParam("hotelId") Long hotelId, @PathParam("tipoId") Long tipoId) {
        servicio.darDeBajaTipo(hotelId, tipoId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{hotelId}/habitaciones")
    public Response crearHabitacion(@PathParam("hotelId") Long hotelId, HabitacionRequest solicitud,
                                    @Context UriInfo uriInfo) {
        HabitacionResponse creada = servicio.crearHabitacion(hotelId, solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(creada.id().toString()).build())
                .entity(creada).build();
    }

    @GET
    @Path("/{hotelId}/habitaciones")
    public List<HabitacionResponse> listarHabitaciones(
            @PathParam("hotelId") Long hotelId,
            @QueryParam("incluirInactivas") @DefaultValue("false") boolean incluirInactivas) {
        return servicio.listarHabitaciones(hotelId, incluirInactivas);
    }

    @GET
    @Path("/{hotelId}/habitaciones/{habitacionId}")
    public HabitacionResponse consultarHabitacion(
            @PathParam("hotelId") Long hotelId,
            @PathParam("habitacionId") Long habitacionId) {
        return servicio.consultarHabitacion(hotelId, habitacionId);
    }

    @PUT
    @Path("/{hotelId}/habitaciones/{habitacionId}")
    public HabitacionResponse modificarHabitacion(@PathParam("hotelId") Long hotelId,
            @PathParam("habitacionId") Long habitacionId, HabitacionRequest solicitud) {
        return servicio.modificarHabitacion(hotelId, habitacionId, solicitud);
    }

    @DELETE
    @Path("/{hotelId}/habitaciones/{habitacionId}")
    public Response eliminarHabitacion(@PathParam("hotelId") Long hotelId,
                                       @PathParam("habitacionId") Long habitacionId) {
        servicio.darDeBajaHabitacion(hotelId, habitacionId);
        return Response.noContent().build();
    }
}
