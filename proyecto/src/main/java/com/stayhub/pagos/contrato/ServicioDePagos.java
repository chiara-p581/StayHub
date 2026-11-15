package com.stayhub.pagos.contrato;

import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoLoteRequest;
import com.stayhub.pagos.dto.PagoResponse;
import com.stayhub.pagos.dto.PagoAdminResponse;
import com.stayhub.pagos.dto.ReembolsoRequest;
import com.stayhub.seguridad.PaginaResponse;
import java.math.BigDecimal;
import java.util.List;

public interface ServicioDePagos {

    PagoResponse procesarPago(PagoRequest solicitud);

    PagoResponse procesarLote(PagoLoteRequest solicitud);

    PagoResponse consultarPago(Long id);

    List<PagoResponse> listarPorReservas(List<Long> reservaIds);
    List<PagoResponse> listarMios(int offset, int limite);
    PaginaResponse<PagoAdminResponse> buscarAdmin(Long id, String usuario, Long hotelId,
            BigDecimal montoMin, BigDecimal montoMax, String estado, int offset, int limite);
    PagoAdminResponse consultarAdmin(Long id);
    PagoAdminResponse reembolsar(Long id, ReembolsoRequest solicitud);
}
