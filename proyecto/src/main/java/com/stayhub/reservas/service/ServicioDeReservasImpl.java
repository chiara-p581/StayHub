package com.stayhub.reservas.service;

import com.stayhub.seguridad.*;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.pagos.repository.PagoRepository;
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

import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

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
 *
 * La autorización HTTP se aplica en AutenticacionFilter a partir de la sesión
 * creada por ServicioDeUsuarios. El servicio conserva @PermitAll porque también
 * recibe invocaciones internas desde el carrito y desde Canales Externos; no debe
 * depender de un segundo usuario duplicado en el realm de WildFly.
 */
@Stateless
public class ServicioDeReservasImpl implements ServicioDeReservasPort, ServicioDeReservas {

    @Inject private IdentidadActual identidad;
    @Inject private ServicioDeUsuarios usuarios;
    @Inject private ServicioDeInventarioYTarifas tarifas;
    @Inject private PagoRepository pagos;

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

    /** 256 locks acotados por referencia. La restricción única en DB protege duplicados entre nodos. */
    private static final Object[] CANDADOS = java.util.stream.IntStream.range(0, 256).mapToObj(i -> new Object()).toArray();
    private Object candadoPara(String canal, String referenciaExterna) {
        return CANDADOS[Math.floorMod(java.util.Objects.hash(canal, referenciaExterna), CANDADOS.length)];
    }

    // ------------------------------------------------------------------
    // ServicioDeReservasPort (llamado por ServicioDeCanalesExternos)
    // ------------------------------------------------------------------

    @Override
    @PermitAll
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
    @PermitAll
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
    @PermitAll
    public ResultadoOperacionReserva cancelarDesdeCanal(String canal, String referenciaExterna) {
        synchronized (candadoPara(canal, referenciaExterna)) {
            Reserva reserva = repositorio.buscarPorCanalYReferencia(canal, referenciaExterna)
                    .orElseThrow(() -> noEncontrada(canal, referenciaExterna));

            if (reserva.getEstado() == EstadoReserva.CANCELADA) {
                // Cancelación duplicada/fuera de orden: ya está cancelada, no repetimos
                // la liberación del hold (evita tocar cupo que ya fue devuelto).
                return ReservaMapper.aResultadoOperacion(reserva);
            }

            exigirTransicion(reserva, EstadoReserva.CANCELADA);

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
    @PermitAll
    public ReservaResponse crearReserva(ReservaRequest solicitud) {
        identidad.exigirHuesped();
        var usuario = usuarios.buscarPorId(identidad.usuarioId());
        validarDirecta(solicitud);
        repositorio.bloquearUsuario(usuario.id());
        if (repositorio.contarPendientesPorUsuario(usuario.id()) >= 10)
            throw new SeguridadException(429, "Tenés demasiadas reservas pendientes de pago");
        var tarifa = precio(solicitud.hotelId(), solicitud.tipoHabitacion(), solicitud.cantidadHabitaciones(), solicitud.checkIn(), solicitud.checkOut());
        ReservaRequest segura = new ReservaRequest(solicitud.hotelId(), solicitud.tipoHabitacion(), solicitud.cantidadHabitaciones(),
                solicitud.checkIn(), solicitud.checkOut(), usuario.nombre(), usuario.apellido(), usuario.email(),
                solicitud.huespedTelefono(), tarifa.importe().multiply(java.math.BigDecimal.valueOf(solicitud.cantidadHabitaciones())), tarifa.moneda());
        Reserva reserva = ReservaMapper.nuevaDirecta(segura);
        reserva.asignarPropietario(usuario.id());
        iniciarHold(reserva);
        repositorio.guardar(reserva);
        return ReservaMapper.aResponse(reserva);
    }

    @Override
    @PermitAll
    public ReservaResponse consultarReserva(Long id) {
        Reserva reserva = buscarOFallar(id);
        identidad.autorizarReserva(reserva);
        return ReservaMapper.aResponse(reserva);
    }

    @Override
    @PermitAll
    public ReservaResponse confirmarReserva(Long id) {
        Reserva reserva = buscarBloqueando(id);
        identidad.autorizarReserva(reserva);
        if (!pagos.existeAprobadoParaReserva(id)) throw new SeguridadException(409, "La reserva requiere un pago aprobado");
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) return ReservaMapper.aResponse(reserva);
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
    @PermitAll
    public ReservaResponse cancelarReserva(Long id) {
        Reserva reserva = buscarBloqueando(id);
        identidad.autorizarReserva(reserva);
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            // Cancelación repetida sobre la misma reserva: no repetimos la
            // liberación del hold (mismo criterio que cancelarDesdeCanal).
            return ReservaMapper.aResponse(reserva);
        }
        exigirTransicion(reserva, EstadoReserva.CANCELADA);
        liberarHoldSiExiste(reserva);
        reserva.cancelar();
        repositorio.guardar(reserva);
        return ReservaMapper.aResponse(reserva);
    }

    /** Solo reservas pendientes: un cambio de una reserva pagada necesita ajuste de pago explícito. */
    @Override
    @PermitAll
    public ReservaResponse modificarReserva(Long id, ReservaRequest solicitud) {
        Reserva reserva = buscarBloqueando(id);
        identidad.autorizarReserva(reserva);
        if (reserva.getEstado() == EstadoReserva.CANCELADA || reserva.getEstado() == EstadoReserva.RECHAZADA) {
            throw new ReservaException(CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA,
                    "No se puede modificar una reserva en estado " + reserva.getEstado());
        }
        validarModificacion(solicitud);
        if (pagos.existeAprobadoParaReserva(id) || reserva.getEstado() == EstadoReserva.CONFIRMADA)
            throw new SeguridadException(409, "Una reserva pagada requiere un flujo de ajuste de pago para modificarla");
        var tarifa = precio(reserva.getHotelId(), solicitud.tipoHabitacion(), solicitud.cantidadHabitaciones(), solicitud.checkIn(), solicitud.checkOut());
        if (!tarifa.moneda().equals(reserva.getMoneda())) throw new SeguridadException(409, "La modificación no puede cambiar de moneda");
        solicitud = new ReservaRequest(reserva.getHotelId(), solicitud.tipoHabitacion(), solicitud.cantidadHabitaciones(), solicitud.checkIn(), solicitud.checkOut(),
                null, null, null, null, tarifa.importe().multiply(java.math.BigDecimal.valueOf(solicitud.cantidadHabitaciones())), tarifa.moneda());

        if (reserva.getHoldId() == null) {
            reserva.actualizarDatos(solicitud.checkIn(), solicitud.checkOut(), solicitud.tipoHabitacion(),
                    solicitud.cantidadHabitaciones(), solicitud.precioTotal());
            iniciarHold(reserva);
        } else {
            String nuevoHoldId;
            try {
                nuevoHoldId = disponibilidad().reemplazarHold(reserva.getHoldId(), reserva.getHotelId(),
                        solicitud.tipoHabitacion(), solicitud.cantidadHabitaciones(),
                        solicitud.checkIn(), solicitud.checkOut());
            } catch (SinDisponibilidadException ex) {
                throw new ReservaException(CodigoErrorReserva.SIN_DISPONIBILIDAD,
                        "No hay disponibilidad para la modificación pedida: la reserva se mantiene sin cambios", ex);
            }
            reserva.actualizarDatos(solicitud.checkIn(), solicitud.checkOut(), solicitud.tipoHabitacion(),
                    solicitud.cantidadHabitaciones(), solicitud.precioTotal());
            reserva.iniciarHold(nuevoHoldId);
        }
        repositorio.guardar(reserva);
        return ReservaMapper.aResponse(reserva);
    }

    @Override
    @PermitAll
    public List<ReservaResponse> listarPorHotel(Long hotelId) {
        return listarPorHotel(hotelId, 0, Limites.RESULTADOS);
    }

    @Override
    @PermitAll
    public List<ReservaResponse> listarPorHuespedEmail(String email) {
        return listarMias(0, Limites.RESULTADOS);
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

    private void exigirTransicion(Reserva reserva, EstadoReserva destino) {
        if (!reserva.getEstado().permiteTransicionA(destino)) {
            throw new ReservaException(CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA,
                    "No se puede cambiar una reserva de " + reserva.getEstado() + " a " + destino);
        }
    }

    private GestionDeDisponibilidadPort disponibilidad() {
        if (!disponibilidad.isResolvable()) {
            throw new ReservaException(CodigoErrorReserva.DEPENDENCIA_NO_DISPONIBLE,
                    "ServicioDeInventarioYTarifas todavía no posee una implementación disponible");
        }
        return disponibilidad.get();
    }

    private Reserva buscarBloqueando(Long id) {
        if (id == null || id <= 0) throw new SeguridadException(404, "Reserva no disponible");
        return repositorio.buscarPorIdBloqueando(id).orElseThrow(() -> new SeguridadException(404, "Reserva no disponible"));
    }
    @Override
    @PermitAll
    public ReservaResponse consultarParaPago(Long id) {
        identidad.exigirHuesped();
        Reserva reserva = buscarBloqueando(id);
        identidad.autorizarReserva(reserva);
        if (reserva.getEstado() != EstadoReserva.PENDIENTE || reserva.getFechaCreacion().isBefore(java.time.LocalDateTime.now().minusMinutes(30)))
            throw new SeguridadException(409, "La reserva no está pendiente o venció");
        return ReservaMapper.aResponse(reserva);
    }

    private Reserva buscarOFallar(Long id) {
        if (id == null || id <= 0) throw new SeguridadException(404, "Reserva no disponible");
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> new SeguridadException(404, "Reserva no disponible"));
    }

    private ReservaException noEncontrada(String canal, String referenciaExterna) {
        return new ReservaException(CodigoErrorReserva.RESERVA_NO_ENCONTRADA,
                "No existe una reserva para canal=" + canal + " referenciaExterna=" + referenciaExterna);
    }

    private void validar(SolicitudReserva s) {
        if (s == null) throw new SeguridadException(400, "Reserva externa inválida");
        Limites.rango(s.checkIn(), s.checkOut()); Limites.cantidad(s.cantidadHabitaciones()); Limites.texto(s.referenciaExterna(), 60, true);
        Limites.texto(s.tipoHabitacion(), 30, true); Limites.texto(s.moneda(), 3, true);
        if (s.huesped() != null) {
            Limites.texto(s.huesped().nombre(), 80, true); Limites.texto(s.huesped().apellido(), 80, true);
            Limites.email(s.huesped().email()); Limites.texto(s.huesped().telefono(), 30, false);
        }
        if (s.precioTotal() != null && (s.precioTotal().precision() > 12 || s.precioTotal().scale() > 2 || s.precioTotal().scale() < 0)) throw new SeguridadException(400, "Precio externo fuera de límites");
        if (s == null || s.referenciaExterna() == null || s.referenciaExterna().isBlank()
                || s.canal() == null || s.canal().isBlank() || s.hotelId() == null
                || s.checkIn() == null || s.checkOut() == null || s.checkIn().isBefore(java.time.LocalDate.now())
                || !s.checkOut().isAfter(s.checkIn())
                || s.tipoHabitacion() == null || s.tipoHabitacion().isBlank()
                || s.cantidadHabitaciones() < 1 || s.huesped() == null || s.precioTotal() == null
                || s.precioTotal().signum() < 0 || s.moneda() == null || s.moneda().isBlank()) {
            throw new ReservaException(CodigoErrorReserva.SOLICITUD_INVALIDA,
                    "La solicitud de reserva está incompleta o contiene valores inválidos");
        }
    }

    /**
     * Igual que validarDirecta, pero sin exigir hotelId ni datos del
     * huésped: una modificación no cambia de hotel ni de titular, solo
     * fechas/tipo/cantidad/precio (ver modificarReserva).
     */
    private void validarModificacion(ReservaRequest s) {
        if (s == null) throw new SeguridadException(400, "Modificación inválida");
        Limites.rango(s.checkIn(), s.checkOut()); Limites.cantidad(s.cantidadHabitaciones()); Limites.texto(s.tipoHabitacion(), 30, true);
        if (s == null
                || s.tipoHabitacion() == null || s.tipoHabitacion().isBlank()
                || s.cantidadHabitaciones() < 1
                || s.checkIn() == null || s.checkOut() == null
                || s.checkIn().isBefore(java.time.LocalDate.now()) || !s.checkOut().isAfter(s.checkIn())
                || s.precioTotal() == null || s.precioTotal().signum() < 0
                || s.moneda() == null || s.moneda().isBlank()) {
            throw new ReservaException(CodigoErrorReserva.SOLICITUD_INVALIDA,
                    "La modificación está incompleta o contiene valores inválidos");
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
        if (s == null || s.hotelId() == null || s.hotelId() <= 0) throw new SeguridadException(400, "Reserva inválida");
        Limites.texto(s.tipoHabitacion(), 30, true); Limites.texto(s.huespedTelefono(), 30, true);
        Limites.cantidad(s.cantidadHabitaciones()); Limites.rango(s.checkIn(), s.checkOut());
        if (s.checkIn().isBefore(java.time.LocalDate.now())) throw new SeguridadException(400, "La reserva no puede iniciar en el pasado");
    }
    private com.stayhub.inventarioytarifas.dto.TarifaDTO precio(Long hotel, String tipo, int cantidad, java.time.LocalDate desde, java.time.LocalDate hasta) {
        Limites.cantidad(cantidad); Limites.rango(desde, hasta);
        var tarifa = tarifas.consultarTarifas(hotel, desde, hasta).stream().filter(t -> tipo.equals(t.tipoHabitacion()))
                .findFirst().orElseThrow(() -> new SeguridadException(409, "Faltan tarifas para todas las noches solicitadas"));
        var total = tarifa.importe().multiply(java.math.BigDecimal.valueOf(cantidad));
        if (total.signum() < 0 || total.scale() > 2 || total.precision() > 12)
            throw new SeguridadException(400, "El total de la reserva supera los límites permitidos");
        return tarifa;
    }
    @Override
    @PermitAll
    public List<ReservaResponse> listarMias(int offset, int limite) {
        return repositorio.listarPorUsuario(identidad.usuarioId(), offset, Limites.pagina(offset, limite)).stream().map(ReservaMapper::aResponse).toList();
    }
    @Override
    @PermitAll
    public List<ReservaResponse> listarPorHotel(Long hotelId, int offset, int limite) {
        identidad.autorizarListado(hotelId);
        return repositorio.listarPorHotel(hotelId, offset, Limites.pagina(offset, limite)).stream().map(ReservaMapper::aResponse).toList();
    }
    @Override
    @PermitAll
    public List<ReservaResponse> listarLlegadas(Long hotelId, java.time.LocalDate fecha) {
        identidad.autorizarHotel(hotelId);
        return repositorio.listarLlegadas(hotelId, fecha).stream().map(ReservaMapper::aResponse).toList();
    }
    @jakarta.ejb.Schedule(hour = "*", minute = "*/5", second = "0", persistent = false)
    public void liberarPendientesVencidas() {
        for (Reserva reserva : repositorio.pendientesVencidas(java.time.LocalDateTime.now().minusMinutes(30))) {
            Reserva bloqueada = buscarBloqueando(reserva.getId());
            if (bloqueada.getEstado() == EstadoReserva.PENDIENTE && !pagos.existeAprobadoParaReserva(bloqueada.getId())) {
                liberarHoldSiExiste(bloqueada); bloqueada.cancelar(); repositorio.guardar(bloqueada);
            }
        }
    }
}
