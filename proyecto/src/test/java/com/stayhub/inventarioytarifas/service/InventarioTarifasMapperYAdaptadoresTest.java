package com.stayhub.inventarioytarifas.service;

import com.stayhub.canalesexternos.dto.TarifaDTO;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import com.stayhub.inventarioytarifas.dto.CargaInventarioResponse;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.exception.CodigoErrorInventarioTarifas;
import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;
import com.stayhub.inventarioytarifas.model.InventarioDiario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mapper de DTOs y adaptadores a los puertos de canales externos y overbooking. */
class InventarioTarifasMapperYAdaptadoresTest {

    private final LocalDate desde = LocalDate.now().plusDays(1);
    private final LocalDate hasta = desde.plusDays(2);

    @Mock ServicioDeInventarioYTarifas nucleo;
    @InjectMocks AdaptadorPortCanalesExternos adaptadorCanales;
    @InjectMocks AdaptadorPortOverbooking adaptadorOverbooking;

    @BeforeEach
    void iniciar() {
        MockitoAnnotations.openMocks(this);
    }

    private InventarioDiario dia(String tipo, LocalDate fecha, int totales, int ocupadas, String precio, String moneda) {
        InventarioDiario d = new InventarioDiario(1L, tipo, fecha, totales, new BigDecimal(precio), moneda);
        if (ocupadas > 0) d.ocupar(ocupadas);
        return d;
    }

    // ---------------- mapper ----------------

    @Test
    void disponibilidadEsElMinimoDeLasNochesDelRango() {
        var dias = List.of(dia("DOBLE", desde, 5, 1, "10", "ARS"), dia("DOBLE", desde.plusDays(1), 5, 3, "10", "ARS"));
        var res = InventarioTarifasMapper.aDisponibilidad(dias, desde, hasta);
        assertEquals(1, res.size());
        assertEquals(new DisponibilidadDTO(1L, "DOBLE", desde, hasta, 2), res.get(0));
    }

    @Test
    void disponibilidadAgrupaPorTipoYDescartaTiposConNochesFaltantes() {
        var dias = List.of(
                dia("DOBLE", desde, 5, 0, "10", "ARS"), dia("DOBLE", desde.plusDays(1), 5, 0, "10", "ARS"),
                dia("SUITE", desde, 2, 0, "50", "ARS"));
        var res = InventarioTarifasMapper.aDisponibilidad(dias, desde, hasta);
        assertEquals(1, res.size());
        assertEquals("DOBLE", res.get(0).tipoHabitacion());
    }

    @Test
    void disponibilidadSinDiasDevuelveListaVacia() {
        assertTrue(InventarioTarifasMapper.aDisponibilidad(List.of(), desde, hasta).isEmpty());
    }

    @Test
    void disponibilidadSobrevendidaPorCambioDeCapacidadSeInformaNegativa() {
        InventarioDiario d = dia("DOBLE", desde, 5, 4, "10", "ARS");
        d.actualizarCapacidadYTarifa(2, BigDecimal.TEN, "ARS");
        var res = InventarioTarifasMapper.aDisponibilidad(List.of(d), desde, desde.plusDays(1));
        assertEquals(-2, res.get(0).unidadesDisponibles());
    }

    @Test
    void tarifaSumaPreciosDeCadaNocheConLaMonedaComun() {
        var dias = List.of(dia("DOBLE", desde, 5, 0, "100.50", "USD"), dia("DOBLE", desde.plusDays(1), 5, 0, "80.25", "USD"));
        var res = InventarioTarifasMapper.aTarifas(dias, desde, hasta);
        assertEquals(1, res.size());
        assertEquals(new com.stayhub.inventarioytarifas.dto.TarifaDTO(1L, "DOBLE", desde, hasta,
                new BigDecimal("180.75"), "USD"), res.get(0));
    }

    @Test
    void tarifaDeUnaSolaNocheEsElPrecioDeEsaNoche() {
        var res = InventarioTarifasMapper.aTarifas(List.of(dia("DOBLE", desde, 1, 0, "7", "ARS")), desde, desde.plusDays(1));
        assertEquals(new BigDecimal("7"), res.get(0).importe());
    }

    @Test
    void tarifaConMasNochesQueElRangoSeDescarta() {
        var dias = List.of(dia("DOBLE", desde, 1, 0, "7", "ARS"), dia("DOBLE", desde.plusDays(1), 1, 0, "7", "ARS"),
                dia("DOBLE", desde.plusDays(2), 1, 0, "7", "ARS"));
        assertTrue(InventarioTarifasMapper.aTarifas(dias, desde, hasta).isEmpty());
    }

    @Test
    void tarifaMantieneTipoValidoCuandoOtroTipoTieneMonedasMezcladas() {
        var dias = List.of(
                dia("DOBLE", desde, 1, 0, "10", "ARS"), dia("DOBLE", desde.plusDays(1), 1, 0, "10", "ARS"),
                dia("SUITE", desde, 1, 0, "10", "ARS"), dia("SUITE", desde.plusDays(1), 1, 0, "10", "USD"));
        var res = InventarioTarifasMapper.aTarifas(dias, desde, hasta);
        assertEquals(1, res.size());
        assertEquals("DOBLE", res.get(0).tipoHabitacion());
    }

    @Test
    void cargaResponseCopiaLosDatosDeLaSolicitudYLosDiasCargados() {
        var req = new CargaInventarioRequest(3L, "SUITE", desde, hasta, 4, BigDecimal.ONE, "ARS");
        CargaInventarioResponse r = InventarioTarifasMapper.aCargaResponse(req, 2);
        assertEquals(new CargaInventarioResponse(3L, "SUITE", desde, hasta, 2), r);
    }

    // ---------------- adaptador canales externos ----------------

    @Test
    void adaptadorCanalesTraduceDisponibilidadCampoACampo() {
        when(nucleo.consultarDisponibilidad(1L, desde, hasta))
                .thenReturn(List.of(new DisponibilidadDTO(1L, "DOBLE", desde, hasta, 4),
                        new DisponibilidadDTO(1L, "SUITE", desde, hasta, 0)));
        var res = adaptadorCanales.consultarDisponibilidad(1L, desde, hasta);
        assertEquals(2, res.size());
        assertEquals(new com.stayhub.canalesexternos.dto.DisponibilidadDTO(1L, "DOBLE", desde, hasta, 4), res.get(0));
        assertEquals(0, res.get(1).unidadesDisponibles());
        verify(nucleo).consultarDisponibilidad(1L, desde, hasta);
    }

    @Test
    void adaptadorCanalesTraduceTarifasCampoACampo() {
        when(nucleo.consultarTarifas(1L, desde, hasta))
                .thenReturn(List.of(new com.stayhub.inventarioytarifas.dto.TarifaDTO(1L, "DOBLE", desde, hasta,
                        new BigDecimal("55.5"), "EUR")));
        List<TarifaDTO> res = adaptadorCanales.consultarTarifas(1L, desde, hasta);
        assertEquals(List.of(new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("55.5"), "EUR")), res);
        verify(nucleo).consultarTarifas(1L, desde, hasta);
    }

    @Test
    void adaptadorCanalesConNucleoSinResultadosDevuelveVacio() {
        when(nucleo.consultarDisponibilidad(1L, desde, hasta)).thenReturn(List.of());
        when(nucleo.consultarTarifas(1L, desde, hasta)).thenReturn(List.of());
        assertTrue(adaptadorCanales.consultarDisponibilidad(1L, desde, hasta).isEmpty());
        assertTrue(adaptadorCanales.consultarTarifas(1L, desde, hasta).isEmpty());
    }

    @Test
    void adaptadorCanalesPropagaLaExcepcionDelNucleo() {
        when(nucleo.consultarTarifas(null, desde, hasta))
                .thenThrow(new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA, "x"));
        InventarioTarifasException ex = assertThrows(InventarioTarifasException.class,
                () -> adaptadorCanales.consultarTarifas(null, desde, hasta));
        assertEquals(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA, ex.getCodigo());
    }

    // ---------------- adaptador overbooking ----------------

    @Test
    void adaptadorOverbookingTraduceDisponibilidadCampoACampo() {
        when(nucleo.consultarDisponibilidad(2L, desde, hasta))
                .thenReturn(List.of(new DisponibilidadDTO(2L, "TRIPLE", desde, hasta, 9)));
        var res = adaptadorOverbooking.consultarDisponibilidad(2L, desde, hasta);
        assertEquals(List.of(new com.stayhub.servicioDeOverbooking.dto.DisponibilidadDTO(2L, "TRIPLE", desde, hasta, 9)), res);
        verify(nucleo).consultarDisponibilidad(2L, desde, hasta);
        verifyNoMoreInteractions(nucleo);
    }

    @Test
    void adaptadorOverbookingSinDisponibilidadDevuelveVacio() {
        when(nucleo.consultarDisponibilidad(2L, desde, hasta)).thenReturn(List.of());
        assertTrue(adaptadorOverbooking.consultarDisponibilidad(2L, desde, hasta).isEmpty());
    }

    @Test
    void adaptadorOverbookingPropagaLaExcepcionDelNucleo() {
        when(nucleo.consultarDisponibilidad(2L, desde, hasta))
                .thenThrow(new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA, "x"));
        assertThrows(InventarioTarifasException.class, () -> adaptadorOverbooking.consultarDisponibilidad(2L, desde, hasta));
    }
}
