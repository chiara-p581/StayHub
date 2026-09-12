package com.stayhub.pagos.service;

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

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
public class ServicioDePagosImpl implements ServicioDePagos {

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
        validar(solicitud);
        verificarNoPagada(solicitud.reservaId());
        var reserva = reservas.consultarReserva(solicitud.reservaId());
        if (reserva.precioTotal().compareTo(solicitud.monto()) != 0
                || !reserva.moneda().equalsIgnoreCase(solicitud.moneda())) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "El monto o la moneda no coinciden con la reserva");
        }

        Pago pago = new Pago(solicitud.reservaId(), solicitud.monto(), solicitud.moneda());
        repositorio.guardar(pago);

        ResultadoCobro resultado = pasarela.cobrar(solicitud.monto(), solicitud.moneda(),
                "reserva-" + solicitud.reservaId());

        if (resultado.aprobado()) {
            pago.aprobar(resultado.referenciaExterna());
            repositorio.guardar(pago);
            publicadorEventos.publicarPagoAprobado(new EventoPagoAprobado(
                    pago.getId(), pago.getReservaId(), pago.getMonto(), pago.getMoneda(),
                    pago.getReferenciaPasarela(), pago.getFechaPago()));
            return PagoMapper.aResponse(pago);
        }

        pago.rechazar();
        repositorio.guardar(pago);
        throw new PagoException(CodigoErrorPago.PAGO_RECHAZADO,
                resultado.motivoRechazo() == null ? "La pasarela rechazó el pago" : resultado.motivoRechazo());
    }

    @Override
    public PagoResponse procesarLote(PagoLoteRequest solicitud) {
        if (solicitud == null || solicitud.reservaIds() == null || solicitud.reservaIds().isEmpty()
                || solicitud.reservaIds().stream().anyMatch(id -> id == null || id <= 0)
                || solicitud.reservaIds().stream().distinct().count() != solicitud.reservaIds().size()
                || solicitud.monto() == null || solicitud.monto().signum() <= 0
                || solicitud.moneda() == null || solicitud.moneda().isBlank()) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "El checkout debe incluir reservas únicas, monto y moneda válidos");
        }
        solicitud.reservaIds().forEach(this::verificarNoPagada);
        BigDecimal totalReal = solicitud.reservaIds().stream().map(reservas::consultarReserva)
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

        Pago pago = new Pago(solicitud.reservaIds(), solicitud.monto(), solicitud.moneda());
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
        solicitud.reservaIds().forEach(reservaId -> publicadorEventos.publicarPagoAprobado(
                new EventoPagoAprobado(pago.getId(), reservaId, pago.getMonto(), pago.getMoneda(),
                        pago.getReferenciaPasarela(), pago.getFechaPago())));
        return PagoMapper.aResponse(pago);
    }

    @Override
    public PagoResponse consultarPago(Long id) {
        Pago pago = repositorio.buscarPorId(id)
                .orElseThrow(() -> new PagoException(CodigoErrorPago.PAGO_NO_ENCONTRADO,
                        "No existe un pago con id " + id));
        return PagoMapper.aResponse(pago);
    }

    private void validar(PagoRequest s) {
        if (s == null || s.reservaId() == null || s.monto() == null || s.monto().signum() <= 0
                || s.moneda() == null || s.moneda().isBlank()) {
            throw new PagoException(CodigoErrorPago.SOLICITUD_INVALIDA,
                    "La solicitud de pago está incompleta o contiene valores inválidos");
        }
    }

    private void verificarNoPagada(Long reservaId) {
        if (repositorio.existeAprobadoParaReserva(reservaId)) {
            throw new PagoException(CodigoErrorPago.RESERVA_YA_PAGADA,
                    "La reserva " + reservaId + " ya tiene un pago aprobado");
        }
    }
}
