package com.stayhub.canalesexternos.service;

import com.stayhub.canalesexternos.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.canalesexternos.contrato.interno.ServicioDeReservasPort;
import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.dto.HuespedDTO;
import com.stayhub.canalesexternos.dto.ReservaExternaDTO;
import com.stayhub.canalesexternos.exception.CanalExternoException;
import com.stayhub.canalesexternos.exception.CodigoErrorCanal;
import com.stayhub.canalesexternos.messaging.PublicadorSincronizacion;
import jakarta.enterprise.inject.Instance;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioDeCanalesExternosImplTest {

    private ServicioDeCanalesExternosImpl servicio;
    private PublicadorSincronizacion publicador;

    @BeforeEach
    void preparar() {
        servicio = new ServicioDeCanalesExternosImpl();
        servicio.reservas = mock(Instance.class);
        servicio.inventarioYTarifas = mock(Instance.class);
        publicador = mock(PublicadorSincronizacion.class);
        servicio.publicadorSincronizacion = publicador;
    }

    @Test
    void sincronizacionOtaDevuelveElMismoIdQueSeEncolo() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 3);
        when(publicador.publicarOta(1L, Canal.BOOKING, desde, hasta)).thenReturn("job-123");

        var resultado = servicio.sincronizarOta(1L, Canal.BOOKING, desde, hasta);

        assertEquals("job-123", resultado.solicitudId());
        assertTrue(resultado.exitoso());
        assertEquals("BOOKING", resultado.destino());
    }

    @Test
    void rechazaHuespedSinEmailAntesDeInvocarReservas() {
        ReservaExternaDTO solicitud = new ReservaExternaDTO(
                "BK-1", Canal.BOOKING, 1L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
                "DOBLE", 1, new HuespedDTO("Ana", "Paz", " ", "111"),
                BigDecimal.TEN, "USD", null);

        CanalExternoException error = assertThrows(CanalExternoException.class,
                () -> servicio.recibirReserva(solicitud));

        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, error.getCodigo());
        verifyNoInteractions(servicio.reservas);
    }

    @Test
    void rechazaPeriodoInvertido() {
        CanalExternoException error = assertThrows(CanalExternoException.class,
                () -> servicio.consultarDisponibilidad(1L,
                        LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));

        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, error.getCodigo());
    }
}
