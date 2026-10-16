package com.stayhub.pagos.service;

import com.stayhub.seguridad.*;
import com.stayhub.pagos.client.pasarela.PasarelaDePagoClient;
import com.stayhub.pagos.client.pasarela.ResultadoCobro;
import com.stayhub.pagos.contrato.ServicioDePagos;
import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoLoteRequest;
import com.stayhub.pagos.dto.PagoResponse;
import com.stayhub.pagos.exception.CodigoErrorPago;
import com.stayhub.pagos.exception.PagoException;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import com.stayhub.pagos.messaging.PublicadorEventoPago;
import com.stayhub.pagos.model.Pago;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.reservas.service.ServicioDeReservas;
import java.math.BigDecimal;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
public class ServicioDePagosImpl implements ServicioDePagos {

    @Inject private IdentidadActual identidad;

    @Inject
    private PagoRepository repositorio;

    @Inject
    private PasarelaDePagoClient pasarela;

    @Inject
    private PublicadorEventoPago publicadorEventos;

    @Inject
    private ServicioDeReservas reservas;

    @Override
    public PagoResponse procesarPago(PagoRequest solicitud) {
        identidad.exigirHuesped();
        validar(solicitud);
        PagoResponse repetido = buscarRepetido(solicitud.claveIdempotencia());
        if (repetido != null) return repetido;
        repositorio.bloquearReservas(List.of(solicitud.reservaId()));
        var reserva = reservas.consultarParaPago(solicitud.reservaId());
        if (!"PENDIENTE".equals(reserva.estado())) throw new SeguridadException(409, "La reserva no está pendiente de pago");
        verificarNoPagada(solicitud.reservaId());
        if (reserva.precioTotal().compareTo(solicitud.monto()) != 0
                || !reserva.moneda().equalsIgnoreCase(solicitud.moneda())) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "El monto o la moneda no coinciden con la reserva");
        }

        Pago pago = new Pago(List.of(solicitud.reservaId()), solicitud.monto(), solicitud.moneda(),
                normalizarClave(solicitud.claveIdempotencia()));
        repositorio.guardar(pago);

        ResultadoCobro resultado = pasarela.cobrar(solicitud.monto(), solicitud.moneda(),
                "reserva-" + solicitud.reservaId());

        if (resultado.aprobado()) {
            pago.aprobar(resultado.referenciaExterna());
            repositorio.guardar(pago);
            var confirmada = reservas.confirmarReserva(solicitud.reservaId());
            publicadorEventos.publicarPagoAprobado(EventoPagoAprobado.desde(pago, confirmada));
            return PagoMapper.aResponse(pago);
        }

        pago.rechazar();
        repositorio.guardar(pago);
        throw new PagoException(CodigoErrorPago.PAGO_RECHAZADO,
                resultado.motivoRechazo() == null ? "La pasarela rechazó el pago" : resultado.motivoRechazo());
    }

    @Override
    public PagoResponse procesarLote(PagoLoteRequest solicitud) {
        identidad.exigirHuesped();
        if (solicitud != null && solicitud.reservaIds() != null && solicitud.reservaIds().size() > Limites.LOTE)
            throw new SeguridadException(400, "El lote supera 20 reservas");
        if (solicitud == null || solicitud.reservaIds() == null || solicitud.reservaIds().isEmpty()
                || solicitud.reservaIds().stream().anyMatch(id -> id == null || id <= 0)
                || solicitud.reservaIds().stream().distinct().count() != solicitud.reservaIds().size()
                || solicitud.monto() == null || solicitud.monto().signum() <= 0
                || solicitud.moneda() == null || solicitud.moneda().isBlank()) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "El checkout debe incluir reservas únicas, monto y moneda válidos");
        }
        PagoResponse repetido = buscarRepetido(solicitud.claveIdempotencia());
        if (repetido != null) return repetido;
        repositorio.bloquearReservas(solicitud.reservaIds());
        // Consultar todas antes de cobrar verifica autorización y estado también para cada ID del lote.
        var reservasAutorizadas = solicitud.reservaIds().stream().sorted().map(reservas::consultarParaPago).toList();
        if (reservasAutorizadas.stream().anyMatch(r -> !"PENDIENTE".equals(r.estado()))) throw new SeguridadException(409, "El lote contiene reservas no pendientes");
        solicitud.reservaIds().forEach(this::verificarNoPagada);
        BigDecimal totalReal = reservasAutorizadas.stream()
                .peek(r -> {
                    if (!r.moneda().equalsIgnoreCase(solicitud.moneda())) {
                        throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                                "Todas las reservas deben usar la misma moneda");
                    }
                }).map(r -> r.precioTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalReal.compareTo(solicitud.monto()) != 0) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "El total del checkout no coincide con las reservas");
        }

        Pago pago = new Pago(solicitud.reservaIds(), solicitud.monto(), solicitud.moneda(),
                normalizarClave(solicitud.claveIdempotencia()));
        repositorio.guardar(pago);
        ResultadoCobro resultado = pasarela.cobrar(solicitud.monto(), solicitud.moneda(),
                "checkout-" + pago.getId());
        if (!resultado.aprobado()) {
            pago.rechazar();
            repositorio.guardar(pago);
            throw new PagoException(CodigoErrorPago.PAGO_RECHAZADO,
                    resultado.motivoRechazo() == null ? "La pasarela rechazó el pago" : resultado.motivoRechazo());
        }

        pago.aprobar(resultado.referenciaExterna());
        repositorio.guardar(pago);
        var confirmadas = solicitud.reservaIds().stream().map(reservas::confirmarReserva).toList();
        confirmadas.forEach(reserva -> publicadorEventos.publicarPagoAprobado(EventoPagoAprobado.desde(pago, reserva)));
        return PagoMapper.aResponse(pago);
    }

    @Override
    public PagoResponse consultarPago(Long id) {
        if (id == null || id <= 0) throw new SeguridadException(404, "Pago no disponible");
        identidad.usuarioId();
        Pago pago = repositorio.buscarPorId(id).orElseThrow(() -> new SeguridadException(404, "Pago no disponible"));
        try {
            for (Long reservaId : pago.getReservaIds()) reservas.consultarReserva(reservaId);
        } catch (SeguridadException ex) {
            if (ex.getEstado() == 404) throw new SeguridadException(404, "Pago no disponible");
            throw ex;
        }
        return PagoMapper.aResponse(pago);
    }

    private void validar(PagoRequest s) {
        if (s == null || s.reservaId() == null || s.monto() == null || s.monto().signum() <= 0
                || s.moneda() == null || s.moneda().isBlank()) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "La solicitud de pago está incompleta o contiene valores inválidos");
        }
    }

    private PagoResponse buscarRepetido(String clave) {
        String normalizada = normalizarClave(clave);
        if (normalizada == null) return null;
        return repositorio.buscarPorClaveIdempotencia(normalizada).map(p -> consultarPago(p.getId())).orElse(null);
    }

    private String normalizarClave(String clave) {
        if (clave == null || clave.isBlank()) return null;
        String valor = clave.trim();
        if (!valor.matches("[A-Za-z0-9._:-]{16,80}")) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "La clave de idempotencia debe tener entre 16 y 80 caracteres seguros");
        }
        return valor;
    }

    @Override
    public List<PagoResponse> listarPorReservas(List<Long> reservaIds) {
        if (reservaIds == null || reservaIds.size() > 100) throw new SeguridadException(400, "Lista de reservas inválida");
        reservaIds.forEach(reservas::consultarReserva);
        return repositorio.listarPorReservas(reservaIds).stream().map(p -> consultarPago(p.getId())).toList();
    }

    private void verificarNoPagada(Long reservaId) {
        if (repositorio.existeAprobadoParaReserva(reservaId)) {
            throw new PagoException(CodigoErrorPago.RESERVA_YA_PAGADA,
                    "La reserva " + reservaId + " ya tiene un pago aprobado");
        }
    }

    @Override public List<PagoResponse> listarMios(int offset, int limite) {
        return repositorio.listarPorUsuario(identidad.usuarioId(), offset, Limites.pagina(offset, limite)).stream().map(p -> consultarPago(p.getId())).toList();
    }
}
