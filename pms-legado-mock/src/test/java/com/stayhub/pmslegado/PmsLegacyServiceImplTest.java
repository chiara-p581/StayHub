package com.stayhub.pmslegado;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PmsLegacyServiceImplTest {

    private final PmsLegacyServiceImpl servicio = new PmsLegacyServiceImpl();

    @AfterEach
    void limpiarDemora() {
        System.clearProperty("pms.demora.ms");
    }

    @Test
    void aceptaUnaSincronizacionValida() {
        DisponibilidadPms disponibilidad = new DisponibilidadPms();
        disponibilidad.tipoHabitacion = "STANDARD";
        disponibilidad.unidades = 3;
        TarifaPms tarifa = new TarifaPms();
        tarifa.tipoHabitacion = "STANDARD";
        tarifa.importe = BigDecimal.valueOf(120_000);
        tarifa.moneda = "ARS";

        RespuestaPms respuesta = servicio.sincronizarInventario(
                7L, List.of(disponibilidad), List.of(tarifa));

        assertTrue(respuesta.exitoso);
        assertEquals("PMS actualizado: 1 disponibilidades y 1 tarifas", respuesta.mensaje);
    }

    @Test
    void rechazaUnaSincronizacionSinHotel() {
        RespuestaPms respuesta = servicio.sincronizarInventario(null, List.of(), List.of());

        assertFalse(respuesta.exitoso);
        assertEquals("hotelId es obligatorio", respuesta.mensaje);
    }

    @Test
    void conservaLaInterrupcionDuranteUnaDemoraSimulada() {
        System.setProperty("pms.demora.ms", "5000");
        Thread.currentThread().interrupt();
        try {
            RespuestaPms respuesta = servicio.sincronizarInventario(1L, null, null);
            assertTrue(respuesta.exitoso);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
