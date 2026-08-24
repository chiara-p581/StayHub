package com.stayhub.hoteles.api;

import com.stayhub.hoteles.dto.*;
import com.stayhub.hoteles.service.ServicioDeHoteles;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.List;

@Path("/hoteles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HotelResource {
    @Inject private ServicioDeHoteles servicio;

    @POST
    public Response crear(HotelRequest solicitud, @Context UriInfo uriInfo) {
        HotelResponse creado = servicio.crearHotel(solicitud);
        URI ubicacion = uriInfo.getAbsolutePathBuilder().path(creado.id().toString()).build();
        return Response.created(ubicacion).entity(creado).build();
    }
    @GET public List<HotelResponse> listar(@QueryParam("incluirInactivos") @DefaultValue("false") boolean incluir) {
        return servicio.listarHoteles(incluir);
    }
    @GET @Path("/{id}") public HotelResponse consultar(@PathParam("id") Long id) {
        return servicio.consultarHotel(id);
    }
    @PUT @Path("/{id}") public HotelResponse modificar(@PathParam("id") Long id, HotelRequest solicitud) {
        return servicio.modificarHotel(id, solicitud);
    }
    @DELETE @Path("/{id}") public HotelResponse eliminar(@PathParam("id") Long id) {
        return servicio.darDeBajaHotel(id);
    }

    @POST @Path("/{hotelId}/tipos-habitacion")
    public Response crearTipo(@PathParam("hotelId") Long hotelId, TipoHabitacionRequest solicitud,
                              @Context UriInfo uriInfo) {
        TipoHabitacionResponse creado = servicio.crearTipo(hotelId, solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(creado.id().toString()).build())
                .entity(creado).build();
    }
    @PUT @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public TipoHabitacionResponse modificarTipo(@PathParam("hotelId") Long hotelId,
            @PathParam("tipoId") Long tipoId, TipoHabitacionRequest solicitud) {
        return servicio.modificarTipo(hotelId, tipoId, solicitud);
    }
    @DELETE @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public Response eliminarTipo(@PathParam("hotelId") Long hotelId, @PathParam("tipoId") Long tipoId) {
        servicio.darDeBajaTipo(hotelId, tipoId);
        return Response.noContent().build();
    }

    @POST @Path("/{hotelId}/habitaciones")
    public Response crearHabitacion(@PathParam("hotelId") Long hotelId, HabitacionRequest solicitud,
                                    @Context UriInfo uriInfo) {
        HabitacionResponse creada = servicio.crearHabitacion(hotelId, solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(creada.id().toString()).build())
                .entity(creada).build();
    }
    @PUT @Path("/{hotelId}/habitaciones/{habitacionId}")
    public HabitacionResponse modificarHabitacion(@PathParam("hotelId") Long hotelId,
            @PathParam("habitacionId") Long habitacionId, HabitacionRequest solicitud) {
        return servicio.modificarHabitacion(hotelId, habitacionId, solicitud);
    }
    @DELETE @Path("/{hotelId}/habitaciones/{habitacionId}")
    public Response eliminarHabitacion(@PathParam("hotelId") Long hotelId,
                                       @PathParam("habitacionId") Long habitacionId) {
        servicio.darDeBajaHabitacion(hotelId, habitacionId);
        return Response.noContent().build();
    }
}
