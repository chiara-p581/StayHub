package com.stayhub.inventarioytarifas.api;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import com.stayhub.inventarioytarifas.dto.CargaInventarioResponse;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.inventarioytarifas.exception.CodigoErrorInventarioTarifas;
import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;
import com.stayhub.inventarioytarifas.cache.VersionLecturasInventario;
import com.stayhub.infraestructura.cache.CacheLecturas;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeParseException;
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
    private static final Duration TTL_LECTURA = Duration.ofSeconds(10);
    private static final CacheLecturas<Consulta, List<DisponibilidadDTO>> CACHE_DISPONIBILIDAD =
            new CacheLecturas<>(TTL_LECTURA);
    private static final CacheLecturas<Consulta, List<TarifaDTO>> CACHE_TARIFAS =
            new CacheLecturas<>(TTL_LECTURA);

    @Inject
    private ServicioDeInventarioYTarifas servicio;

    @GET
    @Path("/disponibilidad")
    public List<DisponibilidadDTO> disponibilidad(
            @QueryParam("hotelId") Long hotelId,
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {

        LocalDate fechaDesde = aFecha(desde, "desde");
        LocalDate fechaHasta = aFecha(hasta, "hasta");
        Consulta clave = new Consulta(hotelId, fechaDesde, fechaHasta, VersionLecturasInventario.actual());
        return CACHE_DISPONIBILIDAD.obtener(clave,
                () -> List.copyOf(servicio.consultarDisponibilidad(hotelId, fechaDesde, fechaHasta)));
    }

    @GET
    @Path("/tarifas")
    public List<TarifaDTO> tarifas(
            @QueryParam("hotelId") Long hotelId,
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {

        LocalDate fechaDesde = aFecha(desde, "desde");
        LocalDate fechaHasta = aFecha(hasta, "hasta");
        Consulta clave = new Consulta(hotelId, fechaDesde, fechaHasta, VersionLecturasInventario.actual());
        return CACHE_TARIFAS.obtener(clave,
                () -> List.copyOf(servicio.consultarTarifas(hotelId, fechaDesde, fechaHasta)));
    }

    @POST
    @Path("/cargas")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cargar(CargaInventarioRequest solicitud) {
        CargaInventarioResponse resultado = servicio.cargarInventario(solicitud);
        VersionLecturasInventario.invalidar();
        CACHE_DISPONIBILIDAD.invalidarTodo();
        CACHE_TARIFAS.invalidarTodo();
        return Response.status(Response.Status.CREATED).entity(resultado).build();
    }

    /**
     * Los query params se reciben como String y se parsean acá a propósito.
     * Con LocalDate.parse() directo sobre el valor crudo, un parámetro ausente
     * daba NullPointerException y uno mal formado DateTimeParseException:
     * ninguna de las dos pasa por InventarioTarifasExceptionMapper, así que el
     * cliente veía un 500 donde corresponde un 400. Traducirlas acá a
     * InventarioTarifasException mantiene el mismo ErrorDTO que el resto del
     * componente sin registrar mappers globales que afectarían a los demás.
     */
    private LocalDate aFecha(String valor, String nombreParametro) {
        if (valor == null || valor.isBlank()) {
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                    "El parámetro '" + nombreParametro + "' es obligatorio (formato ISO YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException ex) {
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                    "El parámetro '" + nombreParametro + "' no es una fecha ISO válida (YYYY-MM-DD): " + valor, ex);
        }
    }

    private record Consulta(Long hotelId, LocalDate desde, LocalDate hasta, long versionInventario) { }
}
