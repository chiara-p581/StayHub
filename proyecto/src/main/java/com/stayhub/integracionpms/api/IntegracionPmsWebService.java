package com.stayhub.integracionpms.api;

import com.stayhub.integracionpms.dto.DisponibilidadPmsDTO;
import com.stayhub.integracionpms.dto.ReservaPmsDTO;
import com.stayhub.integracionpms.dto.TarifaPmsDTO;
import com.stayhub.integracionpms.exception.CodigoErrorIntegracionPms;
import com.stayhub.integracionpms.exception.IntegracionPmsFault;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.service.ServicioDeReservas;

import jakarta.inject.Inject;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio web SOAP que StayHub expone al PMS legado del hotel.
 *
 * Por qué SOAP: el PMS (Property Management System) de recepción es un
 * sistema "legado" que solo sabe integrarse por SOAP/WSDL. Consulta a
 * StayHub en el momento del check-in: el recepcionista tiene al huésped
 * adelante y necesita la respuesta en el acto, por eso la integración es
 * SINCRÓNICA (request/response) y no por mensajería.
 *
 * Enfoque code-first: esta clase anotada ES el contrato. WildFly genera el
 * WSDL a partir de las anotaciones y lo publica en
 *   http://localhost:8080/StayHub/soap/integracion-pms?wsdl
 * (la URL se define en WEB-INF/web.xml).
 *
 * Patrón Facade: el servicio no tiene lógica de negocio propia. Traduce
 * tipos (DTOs XML) y errores (SOAP Fault) y delega en los componentes que ya
 * existen: ServicioDeReservas y ServicioDeInventarioYTarifas. Así un único
 * WSDL cubre las necesidades del PMS sin acoplar esos componentes a SOAP.
 *
 * Estilo document/literal wrapped: el que recomienda el WS-I Basic Profile y
 * el default de JAX-WS (ver Clase 9, "RPC vs. Document").
 */
@WebService(
        name = "IntegracionPmsPortType",
        serviceName = "IntegracionPmsService",
        portName = "IntegracionPmsPort",
        targetNamespace = IntegracionPmsWebService.NAMESPACE)
@SOAPBinding(
        style = SOAPBinding.Style.DOCUMENT,
        use = SOAPBinding.Use.LITERAL,
        parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public class IntegracionPmsWebService {

    public static final String NAMESPACE = "http://soap.stayhub.com/integracion-pms";

    /** Actor con el que se registran las operaciones que hace el PMS (actúa como ADMIN del hotel). */
    static final String ACTOR_PMS = "pms-legado@stayhub.com";

    private static final Logger LOG = Logger.getLogger(IntegracionPmsWebService.class.getName());

    @Inject
    private ServicioDeReservas reservas;

    @Inject
    private ServicioDeInventarioYTarifas inventario;

    // ------------------------------------------------------------------
    // Operaciones sobre reservas (delegan en ServicioDeReservas)
    // ------------------------------------------------------------------

    /** El recepcionista busca una reserva por su número para hacer el check-in. */
    @WebMethod(operationName = "consultarReserva")
    @WebResult(name = "reserva")
    public ReservaPmsDTO consultarReserva(
            @WebParam(name = "reservaId") Long reservaId) throws IntegracionPmsFault {
        requerido(reservaId, "reservaId");
        return ejecutar(() -> IntegracionPmsMapper.aReserva(reservas.consultarReserva(reservaId)));
    }

    /**
     * Lista de llegadas del día: reservas CONFIRMADAS del hotel cuyo check-in
     * es la fecha indicada. Es lo que el PMS carga cada mañana en recepción.
     */
    @WebMethod(operationName = "listarLlegadas")
    @WebResult(name = "reserva")
    public List<ReservaPmsDTO> listarLlegadas(
            @WebParam(name = "hotelId") Long hotelId,
            @WebParam(name = "fecha") String fecha) throws IntegracionPmsFault {
        requerido(hotelId, "hotelId");
        LocalDate dia = fecha(fecha, "fecha");
        return ejecutar(() -> reservas.listarPorHotel(hotelId).stream()
                .filter(r -> "CONFIRMADA".equals(r.estado()))
                .filter(r -> dia.equals(r.checkIn()))
                .map(IntegracionPmsMapper::aReserva)
                .toList());
    }

    /** No-show o cancelación hecha en el mostrador del hotel. Libera el hold de inventario. */
    @WebMethod(operationName = "cancelarReserva")
    @WebResult(name = "reserva")
    public ReservaPmsDTO cancelarReserva(
            @WebParam(name = "reservaId") Long reservaId) throws IntegracionPmsFault {
        requerido(reservaId, "reservaId");
        return ejecutar(() -> IntegracionPmsMapper.aReserva(
                reservas.cancelarReserva(reservaId, ACTOR_PMS, true)));
    }

    // ------------------------------------------------------------------
    // Operaciones sobre inventario (delegan en ServicioDeInventarioYTarifas)
    // ------------------------------------------------------------------

    /** Cupo disponible por tipo de habitación, para un walk-in o un cambio de habitación. */
    @WebMethod(operationName = "consultarDisponibilidad")
    @WebResult(name = "disponibilidad")
    public List<DisponibilidadPmsDTO> consultarDisponibilidad(
            @WebParam(name = "hotelId") Long hotelId,
            @WebParam(name = "desde") String desde,
            @WebParam(name = "hasta") String hasta) throws IntegracionPmsFault {
        requerido(hotelId, "hotelId");
        LocalDate d = fecha(desde, "desde");
        LocalDate h = fecha(hasta, "hasta");
        return ejecutar(() -> inventario.consultarDisponibilidad(hotelId, d, h).stream()
                .map(IntegracionPmsMapper::aDisponibilidad)
                .toList());
    }

    /** Tarifas vigentes por tipo de habitación en el período. */
    @WebMethod(operationName = "consultarTarifas")
    @WebResult(name = "tarifa")
    public List<TarifaPmsDTO> consultarTarifas(
            @WebParam(name = "hotelId") Long hotelId,
            @WebParam(name = "desde") String desde,
            @WebParam(name = "hasta") String hasta) throws IntegracionPmsFault {
        requerido(hotelId, "hotelId");
        LocalDate d = fecha(desde, "desde");
        LocalDate h = fecha(hasta, "hasta");
        return ejecutar(() -> inventario.consultarTarifas(hotelId, d, h).stream()
                .map(IntegracionPmsMapper::aTarifa)
                .toList());
    }

    // ------------------------------------------------------------------
    // Validación de entrada y traducción de errores a SOAP Fault
    // ------------------------------------------------------------------

    private static void requerido(Object valor, String campo) throws IntegracionPmsFault {
        if (valor == null) {
            throw new IntegracionPmsFault(CodigoErrorIntegracionPms.SOLICITUD_INVALIDA,
                    "El campo '" + campo + "' es obligatorio");
        }
    }

    /** Las fechas viajan como texto AAAA-MM-DD, igual que en el cliente del PMS de Canales Externos. */
    private static LocalDate fecha(String valor, String campo) throws IntegracionPmsFault {
        if (valor == null || valor.isBlank()) {
            throw new IntegracionPmsFault(CodigoErrorIntegracionPms.SOLICITUD_INVALIDA,
                    "El campo '" + campo + "' es obligatorio (formato AAAA-MM-DD)");
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            throw new IntegracionPmsFault(CodigoErrorIntegracionPms.SOLICITUD_INVALIDA,
                    "El campo '" + campo + "' debe tener formato AAAA-MM-DD (recibido: " + valor + ")");
        }
    }

    /**
     * Ejecuta la delegación y convierte cualquier error de los componentes
     * internos en un IntegracionPmsFault, que JAX-WS serializa como SOAP
     * Fault declarado en el WSDL. Las excepciones de negocio pueden llegar
     * envueltas por el contenedor EJB (EJBException), por eso se recorre la
     * cadena de causas.
     */
    private static <T> T ejecutar(Supplier<T> operacion) throws IntegracionPmsFault {
        try {
            return operacion.get();
        } catch (RuntimeException ex) {
            throw traducir(ex);
        }
    }

    static IntegracionPmsFault traducir(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ReservaException re) {
                return new IntegracionPmsFault(CodigoErrorIntegracionPms.desde(re.getCodigo()), re.getMessage(), re);
            }
            if (t instanceof InventarioTarifasException ie) {
                return new IntegracionPmsFault(CodigoErrorIntegracionPms.desde(ie.getCodigo()), ie.getMessage(), ie);
            }
            if (t instanceof IllegalArgumentException ia) {
                return new IntegracionPmsFault(CodigoErrorIntegracionPms.SOLICITUD_INVALIDA, ia.getMessage(), ia);
            }
            if (t.getCause() == t) break;
        }
        LOG.log(Level.SEVERE, "Error inesperado atendiendo al PMS legado", ex);
        return new IntegracionPmsFault(CodigoErrorIntegracionPms.ERROR_INTERNO,
                "Error interno de StayHub al procesar la solicitud del PMS", ex);
    }
}
