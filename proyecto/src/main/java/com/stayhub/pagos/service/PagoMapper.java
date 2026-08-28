package com.stayhub.pagos.service;

import com.stayhub.pagos.dto.PagoResponse;
import com.stayhub.pagos.model.Pago;

final class PagoMapper {

    private PagoMapper() { }

    static PagoResponse aResponse(Pago p) {
        return new PagoResponse(p.getId(), p.getReservaId(), p.getMonto(), p.getMoneda(),
                p.getEstado().name(), p.getReferenciaPasarela(), p.getFechaPago());
    }
}