package com.stayhub.servicioDeOverbooking.api;

import com.stayhub.servicioDeOverbooking.contrato.ServicioDeOverbooking;
import com.stayhub.servicioDeOverbooking.dto.ConflictoReservaDTO;
import com.stayhub.servicioDeOverbooking.dto.ResultadoResolucionOverbookingDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/overbooking")
@Produces(MediaType.APPLICATION_JSON)
public class OverbookingResource {

    @Inject ServicioDeOverbooking servicio;

    @POST
    @Path("/resolver")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResultadoResolucionOverbookingDTO resolver(ConflictoReservaDTO conflicto) {
        return servicio.resolverConflicto(conflicto);
    }
}