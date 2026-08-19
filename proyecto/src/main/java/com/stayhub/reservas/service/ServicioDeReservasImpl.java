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

    // ------------------------------------------------------------------
    // ServicioDeReservasPort (llamado por ServicioDeCanalesExternos)
    // ------------------------------------------------------------------

    @Override
    public ResultadoOperacionReserva crearDesdeCanal(SolicitudReserva solicitud) {
        validar(solicitud);
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

    @Override
    public ResultadoOperacionReserva modificarDesdeCanal(SolicitudReserva solicitud) {
        validar(solicitud);
        Reserva reserva = repositorio.buscarPorCanalYReferencia(solicitud.canal(), solicitud.referenciaExterna())
                .orElseThrow(() -> noEncontrada(solicitud.canal(), solicitud.referenciaExterna()));

        liberarHoldSiExiste(reserva);
        reserva.actualizarDatos(solicitud.checkIn(), solicitud.checkOut(), solicitud.tipoHabitacion(),
                solicitud.cantidadHabitaciones(), solicitud.precioTotal());
        holdYConfirmarEnUnPaso(reserva);
        repositorio.guardar(reserva);
        return ReservaMapper.aResultadoOperacion(reserva);
    }

    @Override
    public ResultadoOperacionReserva cancelarDesdeCanal(String canal, String referenciaExterna) {
        Reserva reserva = repositorio.buscarPorCanalYReferencia(canal, referenciaExterna)
                .orElseThrow(() -> noEncontrada(canal, referenciaExterna));
        liberarHoldSiExiste(reserva);
        reserva.cancelar();
        repositorio.guardar(reserva);
        return ReservaMapper.aResultadoOperacion(reserva);
    }

    // ------------------------------------------------------------------
    // ServicioDeReservas (reservas hechas directamente en StayHub)
    // ------------------------------------------------------------------

    @Override
    public ReservaResponse crearReserva(ReservaRequest solicitud) {
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
}
