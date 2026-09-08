package com.stayhub.pagos.api;

import com.stayhub.pagos.contrato.ServicioDePagos;
import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/pagos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PagoResource {

    @Inject
    private ServicioDePagos servicio;

    @POST
    public Response procesar(PagoRequest solicitud) {
        PagoResponse pago = servicio.procesarPago(solicitud);
        return Response.status(Response.Status.CREATED)
                .location(URI.create("api/pagos/" + pago.id()))
                .entity(pago)
                .build();
    }

    @GET
    @Path("/{id}")
    public PagoResponse consultar(@PathParam("id") Long id) {
        return servicio.consultarPago(id);
    }
}