package com.stayhub.pagos.api;

import com.stayhub.pagos.contrato.ServicioDePagos;
import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoResponse;
import com.stayhub.pagos.dto.PagoLoteRequest;
import com.stayhub.reservas.service.ServicioDeReservas;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;

@Path("/pagos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PagoResource {

    @Inject
    private ServicioDePagos servicio;

    @Inject
    private ServicioDeReservas reservas;

    @Inject
    private ServicioDeUsuarios usuarios;

    @GET
    @Path("/mios")
    public List<PagoResponse> listarMios(@QueryParam("offset") @DefaultValue("0") int offset,
                                       @QueryParam("limite") @DefaultValue("100") int limite) {
        return servicio.listarMios(offset, limite);
    }

    @POST
    public Response procesar(PagoRequest solicitud) {
        PagoResponse pago = servicio.procesarPago(solicitud);
        return Response.status(Response.Status.CREATED)
                .location(URI.create("api/pagos/" + pago.id()))
                .entity(pago)
                .build();
    }

    @POST
    @Path("/lote")
    public Response procesarLote(PagoLoteRequest solicitud) {
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
}
