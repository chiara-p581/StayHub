package com.stayhub.pagos.client.pasarela;

import java.math.BigDecimal;

public interface PasarelaDePagoClient {

    ResultadoCobro cobrar(BigDecimal monto, String moneda, String referenciaReserva);
}