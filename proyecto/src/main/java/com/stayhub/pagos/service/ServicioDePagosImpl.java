package com.stayhub.pagos.service;

import com.stayhub.pagos.client.pasarela.PasarelaDePagoClient;
import com.stayhub.pagos.client.pasarela.ResultadoCobro;
import com.stayhub.pagos.contrato.ServicioDePagos;
import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoResponse;
import com.stayhub.pagos.exception.CodigoErrorPago;
import com.stayhub.pagos.exception.PagoException;
import com.stayhub.pagos.model.Pago;
import com.stayhub.pagos.repository.PagoRepository;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
public class ServicioDePagosImpl implements ServicioDePagos {

    @Inject
    private PagoRepository repositorio;

    @Inject
    private PasarelaDePagoClient pasarela;

    @Override
    public PagoResponse procesarPago(PagoRequest solicitud) {
        validar(solicitud);

        Pago pago = new Pago(solicitud.reservaId(), solicitud.monto(), solicitud.moneda());
        repositorio.guardar(pago);

        ResultadoCobro resultado = pasarela.cobrar(solicitud.monto(), solicitud.moneda(),
                "reserva-" + solicitud.reservaId());

        if (resultado.aprobado()) {
            pago.aprobar(resultado.referenciaExterna());
            repositorio.guardar(pago);
            return PagoMapper.aResponse(pago);
        }

        pago.rechazar();
        repositorio.guardar(pago);
        throw new PagoException(CodigoErrorPago.PAGO_RECHAZADO,
                resultado.motivoRechazo() == null ? "La pasarela rechazó el pago" : resultado.motivoRechazo());
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
}