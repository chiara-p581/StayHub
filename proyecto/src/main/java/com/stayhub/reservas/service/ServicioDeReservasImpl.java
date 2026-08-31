package com.stayhub.reservas.service;

import com.stayhub.canalesexternos.contrato.interno.ResultadoOperacionReserva;
import com.stayhub.canalesexternos.contrato.interno.ServicioDeReservasPort;
import com.stayhub.canalesexternos.contrato.interno.SolicitudReserva;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.contrato.SinDisponibilidadException;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.model.EstadoReserva;
import com.stayhub.reservas.model.Reserva;
import com.stayhub.reservas.repository.ReservaRepository;

import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Núcleo de ServicioDeReservas.
 *
 * Es @Stateless: cada operación recibe todos los datos que necesita como
 * parámetro y no depende de haber "recordado" nada de una llamada anterior
 * (a diferencia de ServicioDeInventarioYTarifas, que sí mantiene el hold
 * abierto entre la consulta de disponibilidad y la confirmación — por eso
 * ese componente es stateful y este no).
 *
 * Implementa DOS interfaces:
 *  - ServicioDeReservasPort: el contrato que espera ServicioDeCanalesExternos
 *    (com.stayhub.canalesexternos.contrato.interno), para reservas que
 *    llegan de una OTA.
 *  - ServicioDeReservas: operaciones "internas", para reservas hechas
 *    directamente en StayHub.
 *
 * Ambos caminos convergen en los métodos privados crear/confirmar/cancelar,
 * para no duplicar la lógica de negocio.
 */
@Stateless
public class ServicioDeReservasImpl implements ServicioDeReservasPort, ServicioDeReservas {

    @Inject
    private ReservaRepository repositorio;

    /**
     * Dependencia opcional: si ServicioDeInventarioYTarifas todavía no fue
     * implementado por el equipo, isResolvable() da false y respondemos
     * DEPENDENCIA_NO_DISPONIBLE en lugar de romper el despliegue — mismo
     * patrón que usa ServicioDeCanalesExternosImpl con sus dependencias.
     */
    @Inject
    private Instance<GestionDeDisponibilidadPort> disponibilidad;

    /**
     * Candado en memoria por combinación canal+referenciaExterna, para
     * evitar la condición de carrera "revisar y después actuar" en
     * crearDesdeCanal/modificarDesdeCanal/cancelarDesdeCanal: los tres
     * primero preguntan si la reserva ya existe (o en qué estado está) y
     * recién después actúan, con una ventana en el medio donde dos pedidos
     * casi simultáneos para la MISMA reserva podrían pisarse (p. ej. crear
     * dos veces la misma reserva de una OTA). Serializar por clave hace que
     * pedidos sobre reservas DISTINTAS sigan corriendo en paralelo sin
     * bloquearse entre sí -- solo se sincronizan los que comparten la misma
     * combinación canal+referencia.
     *
     * static porque el contenedor puede crear varias instancias de este
     * @Stateless para atender pedidos en paralelo: el mapa tiene que ser
     * compartido por todas para que el candado sirva de algo. Esto protege
     * un único nodo de WildFly (que es el despliegue real de este
     * proyecto); no alcanzaría si algún día StayHub corriera en más de un
     * servidor al mismo tiempo.
     */
    private static final ConcurrentHashMap<String, Object> candadosPorReferencia = new ConcurrentHashMap<>();

    private Object candadoPara(String canal, String referenciaExterna) {
        return candadosPorReferencia.computeIfAbsent(canal + "|" + referenciaExterna, k -> new Object());
    }

    // ------------------------------------------------------------------
    // ServicioDeReservasPort (llamado por ServicioDeCanalesExternos)
    // ------------------------------------------------------------------

    @Override
    public ResultadoOperacionReserva crearDesdeCanal(SolicitudReserva solicitud) {
        validar(solicitud);
        synchronized (candadoPara(solicitud.canal(), solicitud.referenciaExterna())) {
            Optional<Reserva> existente = repositorio.buscarPorCanalYReferencia(
                    solicitud.canal(), solicitud.referenciaExterna());
            if (existente.isPresent()) {
                // La OTA reenvió el mismo evento: no duplicamos, devolvemos el estado actual.
                return ReservaMapper.aResultadoOperacion(existente.get());
            }

            Reserva reserva = ReservaMapper.nuevaDesdeCanal(solicitud);
            holdYConfirmarEnUnPaso(reserva);
            repositorio.guardar(reserva);
            return ReservaMapper.aResultadoOperacion(reserva);
        }
    }

    @Override
    public ResultadoOperacionReserva modificarDesdeCanal(SolicitudReserva solicitud) {
        validar(solicitud);
        synchronized (candadoPara(solicitud.canal(), solicitud.referenciaExterna())) {
            Reserva reserva = repositorio.buscarPorCanalYReferencia(solicitud.canal(), solicitud.referenciaExterna())
                    .orElseThrow(() -> noEncontrada(solicitud.canal(), solicitud.referenciaExterna()));

            if (reserva.getEstado() == EstadoReserva.CANCELADA) {
                // Mensaje de modificación fuera de orden sobre una reserva ya cancelada
                // (p. ej. llegó después de un mensaje de cancelación más reciente, algo
                // posible con entrega asincrónica): no la revivimos.
                return ReservaMapper.aResultadoOperacion(reserva);
            }

            reemplazarHoldYConfirmar(reserva, solicitud);
            repositorio.guardar(reserva);
            return ReservaMapper.aResultadoOperacion(reserva);
        }
    }

    @Override
    public ResultadoOperacionReserva cancelarDesdeCanal(String canal, String referenciaExterna) {
        synchronized (candadoPara(canal, referenciaExterna)) {
            Reserva reserva = repositorio.buscarPorCanalYReferencia(canal, referenciaExterna)
                    .orElseThrow(() -> noEncontrada(canal, referenciaExterna));

            if (reserva.getEstado() == EstadoReserva.CANCELADA) {
                // Cancelación duplicada/fuera de orden: ya está cancelada, no repetimos
                // la liberación del hold (evita tocar cupo que ya fue devuelto).
                return ReservaMapper.aResultadoOperacion(reserva);
            }

            liberarHoldSiExiste(reserva);
            reserva.cancelar();
            repositorio.guardar(reserva);
            return ReservaMapper.aResultadoOperacion(reserva);
        }
    }

    // ------------------------------------------------------------------
    // ServicioDeReservas (reservas hechas directamente en StayHub)
    // ------------------------------------------------------------------

    @Override
    public ReservaResponse crearReserva(ReservaRequest solicitud) {
        validarDirecta(solicitud);
        Reserva reserva = ReservaMapper.nuevaDirecta(solicitud);
        iniciarHold(reserva);
        repositorio.guardar(reserva);
        return ReservaMapper.aResponse(reserva);
    }

    @Override
    public ReservaResponse consultarReserva(Long id) {
        return ReservaMapper.aResponse(buscarOFallar(id));
    }

    @Override
    public ReservaResponse confirmarReserva(Long id) {
        Reserva reserva = buscarOFallar(id);
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new ReservaException(CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA,
                    "Solo se puede confirmar una reserva PENDIENTE (estado actual: " + reserva.getEstado() + ")");
        }
        disponibilidad().confirmarHold(reserva.getHoldId());
        reserva.confirmar(reserva.getHoldId());
        repositorio.guardar(reserva);
        return ReservaMapper.aResponse(reserva);
    }

    @Override
    public ReservaResponse cancelarReserva(Long id) {
        Reserva reserva = buscarOFallar(id);
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            // Cancelación repetida sobre la misma reserva: no repetimos la
            // liberación del hold (mismo criterio que cancelarDesdeCanal).
            return ReservaMapper.aResponse(reserva);
        }
        liberarHoldSiExiste(reserva);
        reserva.cancelar();
        repositorio.guardar(reserva);
        return ReservaMapper.aResponse(reserva);
    }

    @Override
    public List<ReservaResponse> listarPorHotel(Long hotelId) {
        return repositorio.listarPorHotel(hotelId).stream().map(ReservaMapper::aResponse).toList();
    }

    // ------------------------------------------------------------------
    // helpers privados compartidos
    // ------------------------------------------------------------------

    /**
     * Flujo DIRECTO (crearReserva): intenta retener el cupo y deja la
     * reserva en PENDIENTE con el hold asociado — todavía falta el paso
     * explícito de confirmarReserva() (p. ej. cuando el usuario completa el
     * pago). Si no hay disponibilidad, la reserva queda RECHAZADA.
     */
    private void iniciarHold(Reserva reserva) {
        try {
            String holdId = disponibilidad().crearHold(reserva.getHotelId(), reserva.getTipoHabitacion(),
                    reserva.getCantidadHabitaciones(), reserva.getCheckIn(), reserva.getCheckOut());
            reserva.iniciarHold(holdId);
        } catch (SinDisponibilidadException ex) {
            reserva.rechazar();
        }
    }

    /**
     * Flujo por CANAL EXTERNO (crearDesdeCanal / modificarDesdeCanal):
     * ServicioDeReservasPort no tiene un segundo método para confirmar, así
     * que una reserva que llega de una OTA se resuelve en un solo paso —
     * hold y confirmación juntos, o rechazo si no hay cupo.
     *
     * TODO: cuando exista ServicioDeOverbooking, un conflicto de
     * disponibilidad debería derivarse ahí de forma asincrónica (JMS) en
     * lugar de rechazar la reserva de inmediato.
     */
    private void holdYConfirmarEnUnPaso(Reserva reserva) {
        try {
            String holdId = disponibilidad().crearHold(reserva.getHotelId(), reserva.getTipoHabitacion(),
                    reserva.getCantidadHabitaciones(), reserva.getCheckIn(), reserva.getCheckOut());
            disponibilidad().confirmarHold(holdId);
            reserva.confirmar(holdId);
        } catch (SinDisponibilidadException ex) {
            reserva.rechazar();
        }
    }

    /**
     * Flujo de MODIFICACIÓN por canal externo. Antes esto era
     * liberarHold(viejo) + crearHold(nuevo) como dos llamadas independientes, y
     * eso convertía una reserva confirmada en una retención temporal: la
     * liberación devolvía el cupo al inventario y, si el pedido nuevo no tenía
     * disponibilidad, la reserva quedaba rechazada con su cupo original ya
     * regalado a cualquier otro pedido.
     *
     * Ahora la sustitución es una sola operación de ServicioDeInventarioYTarifas
     * (GestionDeDisponibilidadPort.reemplazarHold), que verifica el cupo del pedido nuevo
     * antes de soltar el viejo. Si no alcanza, no se modificó nada: la reserva
     * conserva su hold, sus datos y su estado, y se responde 409
     * SIN_DISPONIBILIDAD. Es deliberado que acá NO se rechace la reserva como
     * hace crearDesdeCanal: en un alta no hay nada que preservar, pero en una
     * modificación fallida destruir la reserva original sería peor que no
     * aplicar el cambio.
     */
    private void reemplazarHoldYConfirmar(Reserva reserva, SolicitudReserva solicitud) {
        if (reserva.getHoldId() == null) {
            // La reserva nunca llegó a retener cupo (p. ej. quedó RECHAZADA al
            // crearse porque no había disponibilidad): no hay nada que
            // reemplazar, así que la modificación es un intento nuevo y sí
            // corresponde el rechazo si tampoco hay cupo ahora.
            reserva.actualizarDatos(solicitud.checkIn(), solicitud.checkOut(), solicitud.tipoHabitacion(),
                    solicitud.cantidadHabitaciones(), solicitud.precioTotal());
            holdYConfirmarEnUnPaso(reserva);
            return;
        }

        String nuevoHoldId;
        try {
            nuevoHoldId = disponibilidad().reemplazarHold(reserva.getHoldId(), reserva.getHotelId(),
                    solicitud.tipoHabitacion(), solicitud.cantidadHabitaciones(),
                    solicitud.checkIn(), solicitud.checkOut());
        } catch (SinDisponibilidadException ex) {
            throw new ReservaException(CodigoErrorReserva.SIN_DISPONIBILIDAD,
                    "No hay disponibilidad para la modificación pedida sobre canal=" + solicitud.canal()
                            + " referenciaExterna=" + solicitud.referenciaExterna()
                            + ": la reserva se mantiene sin cambios", ex);
        }
        disponibilidad().confirmarHold(nuevoHoldId);
        reserva.actualizarDatos(solicitud.checkIn(), solicitud.checkOut(), solicitud.tipoHabitacion(),
                solicitud.cantidadHabitaciones(), solicitud.precioTotal());
        reserva.confirmar(nuevoHoldId);
    }

    private void liberarHoldSiExiste(Reserva reserva) {
        if (reserva.getHoldId() != null) {
            disponibilidad().liberarHold(reserva.getHoldId());
        }
    }

    private GestionDeDisponibilidadPort disponibilidad() {
        if (!disponibilidad.isResolvable()) {
            throw new ReservaException(CodigoErrorReserva.DEPENDENCIA_NO_DISPONIBLE,
                    "ServicioDeInventarioYTarifas todavía no posee una implementación disponible");
        }
        return disponibilidad.get();
    }

    private Reserva buscarOFallar(Long id) {
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> new ReservaException(CodigoErrorReserva.RESERVA_NO_ENCONTRADA,
                        "No existe una reserva con id " + id));
    }

    private ReservaException noEncontrada(String canal, String referenciaExterna) {
        return new ReservaException(CodigoErrorReserva.RESERVA_NO_ENCONTRADA,
                "No existe una reserva para canal=" + canal + " referenciaExterna=" + referenciaExterna);
    }

    private void validar(SolicitudReserva s) {
        if (s == null || s.referenciaExterna() == null || s.referenciaExterna().isBlank()
                || s.canal() == null || s.canal().isBlank() || s.hotelId() == null
                || s.checkIn() == null || s.checkOut() == null || !s.checkOut().isAfter(s.checkIn())
                || s.tipoHabitacion() == null || s.tipoHabitacion().isBlank()
                || s.cantidadHabitaciones() < 1 || s.huesped() == null || s.precioTotal() == null
                || s.precioTotal().signum() < 0 || s.moneda() == null || s.moneda().isBlank()) {
            throw new ReservaException(CodigoErrorReserva.SOLICITUD_INVALIDA,
                    "La solicitud de reserva está incompleta o contiene valores inválidos");
        }
    }

    /**
     * Antes NO existía ninguna validación para las reservas directas (a
     * diferencia de validar(SolicitudReserva), que sí se aplica a las que
     * llegan por canal externo). Sin esto, un pedido con fechas invertidas,
     * sin hotelId o con datos incompletos llegaba directo a crearHold()/al
     * repositorio y podía romper con un error interno (500) en lugar de
     * devolver un 400 claro.
     */
    private void validarDirecta(ReservaRequest s) {
        if (s == null || s.hotelId() == null
                || s.tipoHabitacion() == null || s.tipoHabitacion().isBlank()
                || s.cantidadHabitaciones() < 1
                || s.checkIn() == null || s.checkOut() == null || !s.checkOut().isAfter(s.checkIn())
                || s.huespedNombre() == null || s.huespedNombre().isBlank()
                || s.huespedApellido() == null || s.huespedApellido().isBlank()
                || s.huespedEmail() == null || s.huespedEmail().isBlank()
                || s.huespedTelefono() == null || s.huespedTelefono().isBlank()
                || s.precioTotal() == null || s.precioTotal().signum() < 0
                || s.moneda() == null || s.moneda().isBlank()) {
            throw new ReservaException(CodigoErrorReserva.SOLICITUD_INVALIDA,
                    "La reserva está incompleta o contiene valores inválidos");
        }
    }
}
