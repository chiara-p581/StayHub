package com.stayhub.pagos.client.pasarela;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.UUID;

@ApplicationScoped
public class PasarelaDePagoClientMock implements PasarelaDePagoClient {

    @Override
    public ResultadoCobro cobrar(BigDecimal monto, String moneda, String referenciaReserva) {
        if (monto == null || monto.signum() <= 0) {
            return new ResultadoCobro(false, null, "Monto inválido");
        }
        return new ResultadoCobro(true, "MOCK-" + UUID.randomUUID(), null);
    }
}