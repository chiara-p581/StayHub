package com.stayhub.canalesexternos.api;

import com.stayhub.canalesexternos.contrato.ServicioDeCanalesExternos;
import com.stayhub.canalesexternos.dto.*;
import com.stayhub.canalesexternos.exception.CanalExternoException;
import com.stayhub.canalesexternos.exception.CodigoErrorCanal;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Path("/canales-externos")
@Produces(MediaType.APPLICATION_JSON)
public class CanalExternoResource {
    @Inject ServicioDeCanalesExternos servicio;

    @GET @Path("/disponibilidad")
    public Response consultar(@QueryParam("hotelId") Long hotelId, @QueryParam("desde") String desde,
                              @QueryParam("hasta") String hasta) {
        return Response.ok(servicio.consultarDisponibilidad(hotelId, fecha(desde, "desde"),
                fecha(hasta, "hasta"))).build();
    }

    @POST @Path("/otas/{canal}/reservas")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response recibir(@PathParam("canal") String canal, ReservaExternaDTO reserva, @Context UriInfo uriInfo) {
        ReservaExternaDTO normalizada = conCanal(reserva, Canal.desde(canal));
        ResultadoReservaDTO resultado = servicio.recibirReserva(normalizada);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(resultado.idExterno()).build())
                .entity(resultado).build();
    }

    @PUT @Path("/otas/{canal}/reservas/{idExterno}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResultadoReservaDTO modificar(@PathParam("canal") String canal, @PathParam("idExterno") String id,
                                         ReservaExternaDTO reserva) {
        return servicio.modificarReserva(conIdentidad(reserva, Canal.desde(canal), id));
    }

    @DELETE @Path("/otas/{canal}/reservas/{idExterno}")
    public ResultadoReservaDTO cancelar(@PathParam("canal") String canal, @PathParam("idExterno") String id) {
        return servicio.cancelarReserva(Canal.desde(canal), id);
    }

    @POST @Path("/otas/{canal}/sincronizaciones")
    public Response sincronizarOta(@PathParam("canal") String canal,
            @QueryParam("hotelId") Long hotelId, @QueryParam("desde") String desde, @QueryParam("hasta") String hasta) {
        return Response.accepted(servicio.sincronizarOta(hotelId, Canal.desde(canal),
                fecha(desde, "desde"), fecha(hasta, "hasta"))).build();
    }

    @POST @Path("/pms/sincronizaciones")
    public Response sincronizarPms(@QueryParam("hotelId") Long hotelId,
            @QueryParam("desde") String desde, @QueryParam("hasta") String hasta) {
        return Response.accepted(servicio.sincronizarPms(hotelId,
                fecha(desde, "desde"), fecha(hasta, "hasta"))).build();
    }

    private ReservaExternaDTO conCanal(ReservaExternaDTO r, Canal canal) {
        return r == null ? null : conIdentidad(r, canal, r.idExterno());
    }
    private ReservaExternaDTO conIdentidad(ReservaExternaDTO r, Canal canal, String id) {
        if (r == null) return null;
        return new ReservaExternaDTO(id,canal,r.hotelId(),r.checkIn(),r.checkOut(),r.tipoHabitacion(),
                r.cantidadHabitaciones(),r.huesped(),r.precioTotal(),r.moneda(),r.estado());
    }


    private LocalDate fecha(String valor, String parametro) {
        if (valor == null || valor.isBlank()) {
            throw new CanalExternoException(
                    CodigoErrorCanal.SOLICITUD_INVALIDA,
                    "El parámetro " + parametro + " es obligatorio y debe usar el formato AAAA-MM-DD"
            );
        }

        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException ex) {
            throw new CanalExternoException(
                    CodigoErrorCanal.SOLICITUD_INVALIDA,
                    "El parámetro " + parametro + " debe usar el formato AAAA-MM-DD",
                    ex
            );
        }
    }
}
