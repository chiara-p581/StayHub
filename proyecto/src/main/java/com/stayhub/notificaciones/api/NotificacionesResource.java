package com.stayhub.notificaciones.api;

import com.stayhub.notificaciones.contrato.ServicioDeNotificaciones;
import com.stayhub.notificaciones.dto.ResultadoEnvioNotificacionDTO;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.service.PlantillaEmail;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/notificaciones")
@Produces(MediaType.APPLICATION_JSON)
public class NotificacionesResource {

    @Inject ServicioDeNotificaciones servicio;

    @POST
    @Path("/enviar")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResultadoEnvioNotificacionDTO enviar(SolicitudNotificacionDTO solicitud) {
        if (solicitud != null && PlantillaEmail.esHtml(solicitud.mensaje())) {
            throw new BadRequestException("El contenido enviado por la API debe ser texto plano");
        }
        return servicio.enviar(solicitud);
    }
}
