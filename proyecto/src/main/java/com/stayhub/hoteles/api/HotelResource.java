package com.stayhub.hoteles.api;

import com.stayhub.hoteles.dto.*;
import com.stayhub.hoteles.exception.CodigoErrorHotel;
import com.stayhub.hoteles.exception.HotelException;
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
    public HotelResponse consultar(@PathParam("id") String id) {
        return servicio.consultarHotel(parsearId(id, "hotel"));
    }

    @PUT
    @Path("/{id}")
    public HotelResponse modificar(@PathParam("id") String id, HotelRequest solicitud) {
        return servicio.modificarHotel(parsearId(id, "hotel"), solicitud);
    }

    @DELETE
    @Path("/{id}")
    public HotelResponse eliminar(@PathParam("id") String id) {
        return servicio.darDeBajaHotel(parsearId(id, "hotel"));
    }

    @POST
    @Path("/{hotelId}/tipos-habitacion")
    public Response crearTipo(@PathParam("hotelId") String hotelId, TipoHabitacionRequest solicitud,
                              @Context UriInfo uriInfo) {
        TipoHabitacionResponse creado = servicio.crearTipo(parsearId(hotelId, "hotel"), solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(creado.id().toString()).build())
                .entity(creado).build();
    }

    @GET
    @Path("/{hotelId}/tipos-habitacion")
    public List<TipoHabitacionResponse> listarTipos(
            @PathParam("hotelId") String hotelId,
            @QueryParam("incluirInactivos") @DefaultValue("false") boolean incluirInactivos) {
        return servicio.listarTipos(parsearId(hotelId, "hotel"), incluirInactivos);
    }

    @GET
    @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public TipoHabitacionResponse consultarTipo(
            @PathParam("hotelId") String hotelId,
            @PathParam("tipoId") String tipoId) {
        return servicio.consultarTipo(parsearId(hotelId, "hotel"),
                parsearId(tipoId, "tipo de habitación"));
    }

    @PUT
    @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public TipoHabitacionResponse modificarTipo(@PathParam("hotelId") String hotelId,
            @PathParam("tipoId") String tipoId, TipoHabitacionRequest solicitud) {
        return servicio.modificarTipo(parsearId(hotelId, "hotel"),
                parsearId(tipoId, "tipo de habitación"), solicitud);
    }

    @DELETE
    @Path("/{hotelId}/tipos-habitacion/{tipoId}")
    public Response eliminarTipo(@PathParam("hotelId") String hotelId,
                                 @PathParam("tipoId") String tipoId) {
        servicio.darDeBajaTipo(parsearId(hotelId, "hotel"),
                parsearId(tipoId, "tipo de habitación"));
        return Response.noContent().build();
    }

    @POST
    @Path("/{hotelId}/habitaciones")
    public Response crearHabitacion(@PathParam("hotelId") String hotelId, HabitacionRequest solicitud,
                                    @Context UriInfo uriInfo) {
        HabitacionResponse creada = servicio.crearHabitacion(parsearId(hotelId, "hotel"), solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(creada.id().toString()).build())
                .entity(creada).build();
    }

    @GET
    @Path("/{hotelId}/habitaciones")
    public List<HabitacionResponse> listarHabitaciones(
            @PathParam("hotelId") String hotelId,
            @QueryParam("incluirInactivas") @DefaultValue("false") boolean incluirInactivas) {
        return servicio.listarHabitaciones(parsearId(hotelId, "hotel"), incluirInactivas);
    }

    @GET
    @Path("/{hotelId}/habitaciones/{habitacionId}")
    public HabitacionResponse consultarHabitacion(
            @PathParam("hotelId") String hotelId,
            @PathParam("habitacionId") String habitacionId) {
        return servicio.consultarHabitacion(parsearId(hotelId, "hotel"),
                parsearId(habitacionId, "habitación"));
    }

    @PUT
    @Path("/{hotelId}/habitaciones/{habitacionId}")
    public HabitacionResponse modificarHabitacion(@PathParam("hotelId") String hotelId,
            @PathParam("habitacionId") String habitacionId, HabitacionRequest solicitud) {
        return servicio.modificarHabitacion(parsearId(hotelId, "hotel"),
                parsearId(habitacionId, "habitación"), solicitud);
    }

    @DELETE
    @Path("/{hotelId}/habitaciones/{habitacionId}")
    public Response eliminarHabitacion(@PathParam("hotelId") String hotelId,
                                       @PathParam("habitacionId") String habitacionId) {
        servicio.darDeBajaHabitacion(parsearId(hotelId, "hotel"),
                parsearId(habitacionId, "habitación"));
        return Response.noContent().build();
    }

    static Long parsearId(String valor, String campo) {
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException ex) {
            throw new HotelException(CodigoErrorHotel.SOLICITUD_INVALIDA,
                    "El id de " + campo + " debe ser un número positivo");
        }
    }
}
