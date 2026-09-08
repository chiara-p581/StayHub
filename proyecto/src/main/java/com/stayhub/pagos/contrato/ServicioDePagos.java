package com.stayhub.pagos.contrato;

import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoResponse;

public interface ServicioDePagos {

    PagoResponse procesarPago(PagoRequest solicitud);

    PagoResponse consultarPago(Long id);
}