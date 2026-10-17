package com.stayhub.pagos.dto;

import jakarta.json.bind.JsonbBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagoJsonTest {
    @Test void checkoutDeserializaElJsonRealDelNavegador() throws Exception {
        try (var json = JsonbBuilder.create()) {
            var pago = json.fromJson("""
                    {"reservaIds":[13,14],"monto":184000,"moneda":"ARS","claveIdempotencia":"checkout-prueba"}
                    """, PagoLoteRequest.class);
            assertEquals(List.of(13L,14L), pago.reservaIds());
            assertEquals(new BigDecimal("184000"), pago.monto());
            assertEquals("ARS", pago.moneda());
            assertEquals("checkout-prueba", pago.claveIdempotencia());
        }
    }
    @Test void pagoIndividualAdmiteClaveYOmisionesCompatibles() throws Exception {
        try (var json = JsonbBuilder.create()) {
            var pago = json.fromJson("""
                    {"reservaId":13,"monto":184000,"moneda":"ARS","claveIdempotencia":"individual-prueba"}
                    """, PagoRequest.class);
            assertEquals(13L, pago.reservaId());
            assertEquals("individual-prueba", pago.claveIdempotencia());
            assertNull(json.fromJson("{\"reservaId\":13,\"monto\":184000,\"moneda\":\"ARS\"}", PagoRequest.class).claveIdempotencia());
        }
    }
}
