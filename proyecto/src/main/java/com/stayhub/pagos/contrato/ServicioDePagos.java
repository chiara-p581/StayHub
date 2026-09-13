package com.stayhub.pagos.contrato;

import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoLoteRequest;
import com.stayhub.pagos.dto.PagoResponse;
import java.util.List;

public interface ServicioDePagos {

    PagoResponse procesarPago(PagoRequest solicitud);

    PagoResponse procesarLote(PagoLoteRequest solicitud);

    PagoResponse consultarPago(Long id);

    List<PagoResponse> listarPorReservas(List<Long> reservaIds);
}
