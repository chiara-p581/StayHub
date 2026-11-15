package com.stayhub.pagos.api;

import com.stayhub.pagos.contrato.ServicioDePagos;
import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoResponse;
import com.stayhub.pagos.dto.PagoLoteRequest;
import com.stayhub.pagos.dto.PagoAdminResponse;
import com.stayhub.pagos.dto.ReembolsoRequest;
import com.stayhub.seguridad.PaginaResponse;
import java.math.BigDecimal;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import com.stayhub.seguridad.ClaveIdempotencia;

/**
 * Controlador GRASP de Pagos: solo traduce HTTP a llamadas al servicio.
 * Bajo acoplamiento: depende únicamente de ServicioDePagos (se quitaron las
 * dependencias a Reservas y Usuarios que no se usaban).
 */
@Path("/pagos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PagoResource {

    @Inject
    private ServicioDePagos servicio;

    @GET
    @Path("/mios")
    public List<PagoResponse> listarMios(@QueryParam("offset") @DefaultValue("0") int offset,
                                       @QueryParam("limite") @DefaultValue("100") int limite) {
        return servicio.listarMios(offset, limite);
    }

    @POST
    public Response procesar(@HeaderParam("Idempotency-Key") String headerIdempotencia,
                             PagoRequest solicitud) {
        String clave = ClaveIdempotencia.requerida(ClaveIdempotencia.combinar(
                headerIdempotencia, solicitud == null ? null : solicitud.claveIdempotencia()));
        if (solicitud != null) solicitud = new PagoRequest(
                solicitud.reservaId(), solicitud.monto(), solicitud.moneda(), clave);
        PagoResponse pago = servicio.procesarPago(solicitud);
        return Response.status(Response.Status.CREATED)
                .location(URI.create("api/pagos/" + pago.id()))
                .entity(pago)
                .build();
    }

    @POST
    @Path("/lote")
    public Response procesarLote(@HeaderParam("Idempotency-Key") String headerIdempotencia,
                                 PagoLoteRequest solicitud) {
        String clave = ClaveIdempotencia.requerida(ClaveIdempotencia.combinar(
                headerIdempotencia, solicitud == null ? null : solicitud.claveIdempotencia()));
        if (solicitud != null) solicitud = new PagoLoteRequest(
                solicitud.reservaIds(), solicitud.monto(), solicitud.moneda(), clave);
        PagoResponse pago = servicio.procesarLote(solicitud);
        return Response.status(Response.Status.CREATED)
                .location(URI.create("api/pagos/" + pago.id()))
                .entity(pago).build();
    }

    @GET
    @Path("/{id}")
    public PagoResponse consultar(@PathParam("id") Long id) {
        return servicio.consultarPago(id);
    }

    @GET
    @Path("/admin")
    public PaginaResponse<PagoAdminResponse> buscarAdmin(
            @QueryParam("id") Long id,
            @QueryParam("usuario") String usuario,
            @QueryParam("hotelId") Long hotelId,
            @QueryParam("montoMin") BigDecimal montoMin,
            @QueryParam("montoMax") BigDecimal montoMax,
            @QueryParam("estado") String estado,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limite") @DefaultValue("10") int limite) {
        return servicio.buscarAdmin(id, usuario, hotelId, montoMin, montoMax, estado, offset, limite);
    }

    @GET
    @Path("/admin/{id}")
    public PagoAdminResponse consultarAdmin(@PathParam("id") Long id) {
        return servicio.consultarAdmin(id);
    }

    @POST
    @Path("/admin/{id}/reembolso")
    public PagoAdminResponse reembolsar(@PathParam("id") Long id, ReembolsoRequest solicitud) {
        return servicio.reembolsar(id, solicitud);
    }
}
