package com.stayhub.inventarioytarifas.api;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import com.stayhub.inventarioytarifas.dto.CargaInventarioResponse;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.List;

/**
 * Administración propia de inventario y tarifas. No confundir con las
 * consultas de disponibilidad que exponen otros componentes para sus
 * propios flujos (p. ej. /canales-externos/disponibilidad, que delega en
 * ServicioDeInventarioYTarifasPort) — esos son puertos internos, este es el
 * API público del componente.
 *
 * Base: /StayHub/api/inventario-tarifas
 */
@Path("/inventario-tarifas")
@Produces(MediaType.APPLICATION_JSON)
public class InventarioResource {

    @Inject
    private ServicioDeInventarioYTarifas servicio;

    @GET
    @Path("/disponibilidad")
    public List<DisponibilidadDTO> disponibilidad(
            @QueryParam("hotelId") Long hotelId,
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {

        return servicio.consultarDisponibilidad(
                hotelId,
                LocalDate.parse(desde),
                LocalDate.parse(hasta)
        );
    }

    @GET
    @Path("/tarifas")
    public List<TarifaDTO> tarifas(
            @QueryParam("hotelId") Long hotelId,
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {

        return servicio.consultarTarifas(
                hotelId,
                LocalDate.parse(desde),
                LocalDate.parse(hasta)
        );
    }

    @POST
    @Path("/cargas")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cargar(CargaInventarioRequest solicitud) {
        CargaInventarioResponse resultado = servicio.cargarInventario(solicitud);
        return Response.status(Response.Status.CREATED).entity(resultado).build();
    }
}
