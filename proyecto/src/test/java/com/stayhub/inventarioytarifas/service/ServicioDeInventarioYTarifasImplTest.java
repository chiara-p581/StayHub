package com.stayhub.inventarioytarifas.service;

import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import com.stayhub.inventarioytarifas.dto.CargaInventarioResponse;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.inventarioytarifas.exception.CodigoErrorInventarioTarifas;
import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;
import com.stayhub.inventarioytarifas.model.EstadoHold;
import com.stayhub.inventarioytarifas.model.Hold;
import com.stayhub.inventarioytarifas.model.InventarioDiario;
import com.stayhub.inventarioytarifas.repository.HoldRepository;
import com.stayhub.inventarioytarifas.repository.InventarioDiarioRepository;
import com.stayhub.reservas.contrato.SinDisponibilidadException;
import com.stayhub.seguridad.SeguridadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Pruebas del nucleo de inventario y tarifas: consultas, carga y ciclo de vida de holds. */
class ServicioDeInventarioYTarifasImplTest {

    private static final Long HOTEL = 1L;
    private static final String DOBLE = "DOBLE";

    @Mock InventarioDiarioRepository inventarioRepositorio;
    @Mock HoldRepository holdRepositorio;
    @InjectMocks ServicioDeInventarioYTarifasImpl servicio;

    private final LocalDate checkIn = LocalDate.now().plusDays(5);
    private final LocalDate checkOut = checkIn.plusDays(2);

    @BeforeEach
    void iniciar() {
        MockitoAnnotations.openMocks(this);
    }

    // ---------------- helpers ----------------

    private InventarioDiario cargarDia(String tipo, LocalDate fecha, int totales, int ocupadas) {
        InventarioDiario d = new InventarioDiario(HOTEL, tipo, fecha, totales, new BigDecimal("100"), "ARS");
        if (ocupadas > 0) d.ocupar(ocupadas);
        when(inventarioRepositorio.buscarPorHotelTipoYFechaBloqueando(HOTEL, tipo, fecha)).thenReturn(Optional.of(d));
        return d;
    }

    private Hold holdExistente(String id, String tipo, int cantidad, LocalDate in, LocalDate out) {
        Hold h = new Hold(id, HOTEL, tipo, cantidad, in, out);
        when(holdRepositorio.buscarPorId(id)).thenReturn(Optional.of(h));
        return h;
    }

    private InventarioTarifasException assertInventario(CodigoErrorInventarioTarifas codigo, org.junit.jupiter.api.function.Executable e) {
        InventarioTarifasException ex = assertThrows(InventarioTarifasException.class, e);
        assertEquals(codigo, ex.getCodigo());
        return ex;
    }

    // ---------------- consultas ----------------

    @Test
    void consultarDisponibilidadMapeaLasFilasDelRepositorio() {
        when(inventarioRepositorio.buscarPorHotelYRango(HOTEL, checkIn, checkOut)).thenReturn(List.of(
                new InventarioDiario(HOTEL, DOBLE, checkIn, 4, BigDecimal.TEN, "ARS"),
                new InventarioDiario(HOTEL, DOBLE, checkIn.plusDays(1), 3, BigDecimal.TEN, "ARS")));
        List<DisponibilidadDTO> res = servicio.consultarDisponibilidad(HOTEL, checkIn, checkOut);
        assertEquals(List.of(new DisponibilidadDTO(HOTEL, DOBLE, checkIn, checkOut, 3)), res);
        verify(inventarioRepositorio).buscarPorHotelYRango(HOTEL, checkIn, checkOut);
    }

    @Test
    void consultarTarifasSumaLasNochesDelRepositorio() {
        when(inventarioRepositorio.buscarPorHotelYRango(HOTEL, checkIn, checkOut)).thenReturn(List.of(
                new InventarioDiario(HOTEL, DOBLE, checkIn, 4, new BigDecimal("10"), "ARS"),
                new InventarioDiario(HOTEL, DOBLE, checkIn.plusDays(1), 3, new BigDecimal("15"), "ARS")));
        List<TarifaDTO> res = servicio.consultarTarifas(HOTEL, checkIn, checkOut);
        assertEquals(List.of(new TarifaDTO(HOTEL, DOBLE, checkIn, checkOut, new BigDecimal("25"), "ARS")), res);
    }

    @Test
    void consultasSinHotelFallanConSolicitudInvalidaSinTocarElRepositorio() {
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.consultarDisponibilidad(null, checkIn, checkOut));
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.consultarTarifas(null, checkIn, checkOut));
        verifyNoInteractions(inventarioRepositorio);
    }

    @Test
    void consultasConRangosInvalidosFallanPorLimites() {
        assertThrows(SeguridadException.class, () -> servicio.consultarDisponibilidad(HOTEL, checkIn, checkIn));
        assertThrows(SeguridadException.class, () -> servicio.consultarDisponibilidad(HOTEL, checkOut, checkIn));
        assertThrows(SeguridadException.class, () -> servicio.consultarTarifas(HOTEL, null, checkOut));
        assertThrows(SeguridadException.class, () -> servicio.consultarTarifas(HOTEL, checkIn, null));
        assertThrows(SeguridadException.class, () -> servicio.consultarTarifas(HOTEL, checkIn, checkIn.plusDays(32)));
        verifyNoInteractions(inventarioRepositorio);
    }

    @Test
    void consultasAceptanExactamente31Noches() {
        LocalDate fin = checkIn.plusDays(31);
        when(inventarioRepositorio.buscarPorHotelYRango(HOTEL, checkIn, fin)).thenReturn(List.of());
        assertTrue(servicio.consultarDisponibilidad(HOTEL, checkIn, fin).isEmpty());
        assertTrue(servicio.consultarTarifas(HOTEL, checkIn, fin).isEmpty());
    }

    // ---------------- cargarInventario ----------------

    private CargaInventarioRequest carga(Long hotel, String tipo, LocalDate d, LocalDate h, int unidades, BigDecimal precio, String moneda) {
        return new CargaInventarioRequest(hotel, tipo, d, h, unidades, precio, moneda);
    }

    @Test
    void cargarInventarioDelegaEnElRepositorioYDevuelveLosDiasCargados() {
        when(inventarioRepositorio.cargarRango(HOTEL, DOBLE, checkIn, checkOut, 10, new BigDecimal("99.90"), "ARS")).thenReturn(2);
        CargaInventarioResponse r = servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 10, new BigDecimal("99.90"), "ARS"));
        assertEquals(new CargaInventarioResponse(HOTEL, DOBLE, checkIn, checkOut, 2), r);
        verify(inventarioRepositorio).cargarRango(HOTEL, DOBLE, checkIn, checkOut, 10, new BigDecimal("99.90"), "ARS");
    }

    @Test
    void cargarInventarioAceptaLosLimitesInclusivos() {
        // 10000 unidades, precio cero, precision 12 y escala 2 son valores de borde validos
        when(inventarioRepositorio.cargarRango(any(), anyString(), any(), any(), anyInt(), any(), anyString())).thenReturn(1);
        assertEquals(1, servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 10000, new BigDecimal("0"), "ARS")).diasCargados());
        assertEquals(1, servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 0, new BigDecimal("9999999999.99"), "ARS")).diasCargados());
        verify(inventarioRepositorio, times(2)).cargarRango(any(), anyString(), any(), any(), anyInt(), any(), anyString());
    }

    @Test
    void cargarInventarioNuloFallaPorSeguridad() {
        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.cargarInventario(null));
        assertEquals(400, ex.getEstado());
        verifyNoInteractions(inventarioRepositorio);
    }

    @Test
    void cargarInventarioFueraDeLimitesFallaPorSeguridad() {
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 10001, BigDecimal.ONE, "ARS")));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 1, new BigDecimal("1.001"), "ARS")));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 1, new BigDecimal("99999999999.99"), "ARS")));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 1, BigDecimal.ONE, "ARSS")));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 1, BigDecimal.ONE, null)));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, " ", checkIn, checkOut, 1, BigDecimal.ONE, "ARS")));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, "X".repeat(31), checkIn, checkOut, 1, BigDecimal.ONE, "ARS")));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkOut, checkIn, 1, BigDecimal.ONE, "ARS")));
        assertThrows(SeguridadException.class,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, null, checkOut, 1, BigDecimal.ONE, "ARS")));
        verifyNoInteractions(inventarioRepositorio);
    }

    @Test
    void cargarInventarioConDatosDeNegocioInvalidosFallaConSolicitudInvalida() {
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.cargarInventario(carga(null, DOBLE, checkIn, checkOut, 1, BigDecimal.ONE, "ARS")));
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, -1, BigDecimal.ONE, "ARS")));
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 1, null, "ARS")));
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.cargarInventario(carga(HOTEL, DOBLE, checkIn, checkOut, 1, new BigDecimal("-0.01"), "ARS")));
        verifyNoInteractions(inventarioRepositorio);
    }

    // ---------------- crearHold ----------------

    @Test
    void crearHoldOcupaCadaNocheYPersisteUnHoldPendiente() {
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 5, 1);
        InventarioDiario n2 = cargarDia(DOBLE, checkIn.plusDays(1), 5, 0);

        String id = servicio.crearHold(HOTEL, DOBLE, 2, checkIn, checkOut);

        assertEquals(3, n1.getUnidadesOcupadas());
        assertEquals(2, n2.getUnidadesOcupadas());
        verify(inventarioRepositorio).guardar(n1);
        verify(inventarioRepositorio).guardar(n2);
        ArgumentCaptor<Hold> cap = ArgumentCaptor.forClass(Hold.class);
        verify(holdRepositorio).guardar(cap.capture());
        Hold h = cap.getValue();
        assertEquals(id, h.getId());
        assertEquals(EstadoHold.PENDIENTE, h.getEstado());
        assertEquals(HOTEL, h.getHotelId());
        assertEquals(DOBLE, h.getTipoHabitacion());
        assertEquals(2, h.getCantidadHabitaciones());
        assertEquals(checkIn, h.getCheckIn());
        assertEquals(checkOut, h.getCheckOut());
    }

    @Test
    void crearHoldGeneraIdsDistintosEnCadaLlamada() {
        cargarDia(DOBLE, checkIn, 5, 0);
        cargarDia(DOBLE, checkIn.plusDays(1), 5, 0);
        String a = servicio.crearHold(HOTEL, DOBLE, 1, checkIn, checkOut);
        String b = servicio.crearHold(HOTEL, DOBLE, 1, checkIn, checkOut);
        assertNotEquals(a, b);
    }

    @Test
    void crearHoldConCupoJustoLoDejaEnCero() {
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 3, 1);
        servicio.crearHold(HOTEL, DOBLE, 2, checkIn, checkIn.plusDays(1));
        assertEquals(0, n1.getUnidadesDisponibles());
    }

    @Test
    void crearHoldDesdeHoyEsValido() {
        LocalDate hoy = LocalDate.now();
        InventarioDiario n = cargarDia(DOBLE, hoy, 2, 0);
        servicio.crearHold(HOTEL, DOBLE, 1, hoy, hoy.plusDays(1));
        assertEquals(1, n.getUnidadesOcupadas());
    }

    @Test
    void crearHoldSinCupoEnLaUltimaNocheNoModificaNingunaNocheNiGuardaHold() {
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 5, 0);
        InventarioDiario n2 = cargarDia(DOBLE, checkIn.plusDays(1), 5, 4);

        SinDisponibilidadException ex = assertThrows(SinDisponibilidadException.class,
                () -> servicio.crearHold(HOTEL, DOBLE, 2, checkIn, checkOut));

        assertTrue(ex.getMessage().contains("No hay disponibilidad"));
        assertTrue(ex.getMessage().contains(checkIn.plusDays(1).toString()));
        assertEquals(0, n1.getUnidadesOcupadas());
        assertEquals(4, n2.getUnidadesOcupadas());
        verify(inventarioRepositorio, never()).guardar(any());
        verifyNoInteractions(holdRepositorio);
    }

    @Test
    void crearHoldConCupoAgotadoFalla() {
        cargarDia(DOBLE, checkIn, 2, 2);
        assertThrows(SinDisponibilidadException.class,
                () -> servicio.crearHold(HOTEL, DOBLE, 1, checkIn, checkIn.plusDays(1)));
        verifyNoInteractions(holdRepositorio);
    }

    @Test
    void crearHoldSinInventarioCargadoInformaLaFaltaDeInventario() {
        when(inventarioRepositorio.buscarPorHotelTipoYFechaBloqueando(any(), anyString(), any())).thenReturn(Optional.empty());
        SinDisponibilidadException ex = assertThrows(SinDisponibilidadException.class,
                () -> servicio.crearHold(HOTEL, DOBLE, 1, checkIn, checkOut));
        assertTrue(ex.getMessage().contains("No hay inventario cargado"));
        assertTrue(ex.getMessage().contains(checkIn.toString()));
        verify(inventarioRepositorio, never()).guardar(any());
    }

    @Test
    void crearHoldBloqueaLasNochesEnOrdenCronologico() {
        cargarDia(DOBLE, checkIn, 5, 0);
        cargarDia(DOBLE, checkIn.plusDays(1), 5, 0);
        servicio.crearHold(HOTEL, DOBLE, 1, checkIn, checkOut);
        InOrder orden = inOrder(inventarioRepositorio);
        orden.verify(inventarioRepositorio).buscarPorHotelTipoYFechaBloqueando(HOTEL, DOBLE, checkIn);
        orden.verify(inventarioRepositorio).buscarPorHotelTipoYFechaBloqueando(HOTEL, DOBLE, checkIn.plusDays(1));
    }

    @Test
    void crearHoldConPedidoInvalidoFallaSinTocarRepositorios() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        // validaciones propias del servicio
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.crearHold(null, DOBLE, 1, checkIn, checkOut));
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.crearHold(HOTEL, DOBLE, 1, ayer, LocalDate.now().plusDays(1)));
        // validaciones de Limites (se ejecutan primero)
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, 0, checkIn, checkOut));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, -3, checkIn, checkOut));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, 11, checkIn, checkOut));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, null, 1, checkIn, checkOut));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, "  ", 1, checkIn, checkOut));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, 1, checkIn, checkIn));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, 1, checkOut, checkIn));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, 1, null, checkOut));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, 1, checkIn, null));
        assertThrows(SeguridadException.class, () -> servicio.crearHold(HOTEL, DOBLE, 1, checkIn, checkIn.plusDays(32)));
        verifyNoInteractions(inventarioRepositorio, holdRepositorio);
    }

    @Test
    void crearHoldAceptaDiezHabitacionesYTreintaYUnaNoches() {
        LocalDate fin = checkIn.plusDays(31);
        for (LocalDate f = checkIn; f.isBefore(fin); f = f.plusDays(1)) cargarDia(DOBLE, f, 10, 0);
        servicio.crearHold(HOTEL, DOBLE, 10, checkIn, fin);
        verify(inventarioRepositorio, times(31)).guardar(any(InventarioDiario.class));
    }

    // ---------------- confirmarHold ----------------

    @Test
    void confirmarHoldPendienteLoConfirmaYLoGuarda() {
        Hold h = holdExistente("h1", DOBLE, 1, checkIn, checkOut);
        servicio.confirmarHold("h1");
        assertEquals(EstadoHold.CONFIRMADO, h.getEstado());
        verify(holdRepositorio).guardar(h);
        verifyNoInteractions(inventarioRepositorio);
    }

    @Test
    void confirmarHoldInexistenteLanzaHoldNoEncontrado() {
        when(holdRepositorio.buscarPorId("nope")).thenReturn(Optional.empty());
        InventarioTarifasException ex = assertInventario(CodigoErrorInventarioTarifas.HOLD_NO_ENCONTRADO,
                () -> servicio.confirmarHold("nope"));
        assertTrue(ex.getMessage().contains("nope"));
        verify(holdRepositorio, never()).guardar(any());
    }

    @Test
    void confirmarHoldYaConfirmadoLanzaTransicionInvalida() {
        Hold h = holdExistente("h1", DOBLE, 1, checkIn, checkOut);
        h.confirmar();
        InventarioTarifasException ex = assertInventario(CodigoErrorInventarioTarifas.TRANSICION_DE_ESTADO_INVALIDA,
                () -> servicio.confirmarHold("h1"));
        assertTrue(ex.getMessage().contains("CONFIRMADO"));
        verify(holdRepositorio, never()).guardar(any());
    }

    @Test
    void confirmarHoldLiberadoLanzaTransicionInvalidaYNoLoResucita() {
        Hold h = holdExistente("h1", DOBLE, 1, checkIn, checkOut);
        h.liberar();
        assertInventario(CodigoErrorInventarioTarifas.TRANSICION_DE_ESTADO_INVALIDA, () -> servicio.confirmarHold("h1"));
        assertEquals(EstadoHold.LIBERADO, h.getEstado());
    }

    // ---------------- liberarHold ----------------

    @Test
    void liberarHoldPendienteDevuelveElCupoDeCadaNoche() {
        Hold h = holdExistente("h1", DOBLE, 2, checkIn, checkOut);
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 5, 3);
        InventarioDiario n2 = cargarDia(DOBLE, checkIn.plusDays(1), 5, 2);

        servicio.liberarHold("h1");

        assertEquals(1, n1.getUnidadesOcupadas());
        assertEquals(0, n2.getUnidadesOcupadas());
        assertEquals(EstadoHold.LIBERADO, h.getEstado());
        verify(inventarioRepositorio).guardar(n1);
        verify(inventarioRepositorio).guardar(n2);
        verify(holdRepositorio).guardar(h);
    }

    @Test
    void liberarHoldConfirmadoTambienDevuelveElCupo() {
        Hold h = holdExistente("h1", DOBLE, 1, checkIn, checkIn.plusDays(1));
        h.confirmar();
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 5, 1);
        servicio.liberarHold("h1");
        assertEquals(0, n1.getUnidadesOcupadas());
        assertEquals(EstadoHold.LIBERADO, h.getEstado());
    }

    @Test
    void liberarHoldYaLiberadoEsIdempotenteYNoDevuelveCupoDosVeces() {
        Hold h = holdExistente("h1", DOBLE, 2, checkIn, checkOut);
        h.liberar();
        servicio.liberarHold("h1");
        verifyNoInteractions(inventarioRepositorio);
        verify(holdRepositorio, never()).guardar(any());
        assertEquals(EstadoHold.LIBERADO, h.getEstado());
    }

    @Test
    void liberarHoldInexistenteLanzaHoldNoEncontrado() {
        when(holdRepositorio.buscarPorId("x")).thenReturn(Optional.empty());
        assertInventario(CodigoErrorInventarioTarifas.HOLD_NO_ENCONTRADO, () -> servicio.liberarHold("x"));
        verifyNoInteractions(inventarioRepositorio);
    }

    @Test
    void liberarHoldIgnoraNochesSinFilaDeInventarioYLiberaLasDemas() {
        Hold h = holdExistente("h1", DOBLE, 1, checkIn, checkOut);
        when(inventarioRepositorio.buscarPorHotelTipoYFechaBloqueando(HOTEL, DOBLE, checkIn)).thenReturn(Optional.empty());
        InventarioDiario n2 = cargarDia(DOBLE, checkIn.plusDays(1), 5, 1);

        servicio.liberarHold("h1");

        assertEquals(0, n2.getUnidadesOcupadas());
        verify(inventarioRepositorio, times(1)).guardar(any(InventarioDiario.class));
        assertEquals(EstadoHold.LIBERADO, h.getEstado());
    }

    // ---------------- reemplazarHold ----------------

    @Test
    void reemplazarHoldUsaElCreditoDelAnteriorAlAumentarHabitacionesEnLasMismasNoches() {
        Hold anterior = holdExistente("ant", DOBLE, 2, checkIn, checkOut);
        // cupo total 3, ya ocupado 2 por el hold anterior: libre 1, +2 de credito = 3
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 3, 2);
        InventarioDiario n2 = cargarDia(DOBLE, checkIn.plusDays(1), 3, 2);

        String nuevo = servicio.reemplazarHold("ant", HOTEL, DOBLE, 3, checkIn, checkOut);

        assertEquals(3, n1.getUnidadesOcupadas());
        assertEquals(3, n2.getUnidadesOcupadas());
        assertEquals(EstadoHold.LIBERADO, anterior.getEstado());
        ArgumentCaptor<Hold> cap = ArgumentCaptor.forClass(Hold.class);
        verify(holdRepositorio, times(2)).guardar(cap.capture());
        Hold creado = cap.getAllValues().get(1);
        assertEquals(nuevo, creado.getId());
        assertNotEquals("ant", nuevo);
        assertEquals(EstadoHold.PENDIENTE, creado.getEstado());
        assertEquals(3, creado.getCantidadHabitaciones());
    }

    @Test
    void reemplazarHoldSinCupoNoDejaAlAnteriorSinSuRetencion() {
        Hold anterior = holdExistente("ant", DOBLE, 2, checkIn, checkOut);
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 3, 2);
        InventarioDiario n2 = cargarDia(DOBLE, checkIn.plusDays(1), 3, 2);

        // pide 4 con total 3: aun con el credito (1 + 2 = 3) no alcanza
        assertThrows(SinDisponibilidadException.class,
                () -> servicio.reemplazarHold("ant", HOTEL, DOBLE, 4, checkIn, checkOut));

        assertEquals(2, n1.getUnidadesOcupadas());
        assertEquals(2, n2.getUnidadesOcupadas());
        assertEquals(EstadoHold.PENDIENTE, anterior.getEstado());
        verify(inventarioRepositorio, never()).guardar(any());
        verify(holdRepositorio, never()).guardar(any());
    }

    @Test
    void reemplazarHoldPorOtrasFechasLiberaLasViejasYOcupaLasNuevas() {
        LocalDate nIn = checkIn.plusDays(10);
        Hold anterior = holdExistente("ant", DOBLE, 1, checkIn, checkIn.plusDays(1));
        InventarioDiario vieja = cargarDia(DOBLE, checkIn, 5, 1);
        InventarioDiario nueva = cargarDia(DOBLE, nIn, 5, 5 - 1);

        servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, nIn, nIn.plusDays(1));

        assertEquals(0, vieja.getUnidadesOcupadas());
        assertEquals(5, nueva.getUnidadesOcupadas());
        assertEquals(EstadoHold.LIBERADO, anterior.getEstado());
    }

    @Test
    void reemplazarHoldPorFechasSolapadasBloqueaCadaNocheUnaSolaVez() {
        // anterior: noches 5 y 6; nuevo: noches 6 y 7 (la noche 6 se solapa)
        Hold anterior = holdExistente("ant", DOBLE, 1, checkIn, checkOut);
        cargarDia(DOBLE, checkIn, 5, 1);
        cargarDia(DOBLE, checkIn.plusDays(1), 5, 1);
        cargarDia(DOBLE, checkIn.plusDays(2), 5, 0);

        servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, checkIn.plusDays(1), checkIn.plusDays(3));

        verify(inventarioRepositorio, times(1)).buscarPorHotelTipoYFechaBloqueando(HOTEL, DOBLE, checkIn.plusDays(1));
        assertEquals(EstadoHold.LIBERADO, anterior.getEstado());
    }

    @Test
    void reemplazarHoldBloqueaEnOrdenPorTipoYLuegoFechaSinImportarElOrdenDelPedido() {
        // anterior SUITE, nuevo DOBLE: "DOBLE" < "SUITE" => se bloquea DOBLE primero
        Hold anterior = holdExistente("ant", "SUITE", 1, checkIn, checkIn.plusDays(1));
        cargarDia("SUITE", checkIn, 2, 1);
        cargarDia(DOBLE, checkIn, 2, 0);

        servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, checkIn, checkIn.plusDays(1));

        InOrder orden = inOrder(inventarioRepositorio);
        orden.verify(inventarioRepositorio).buscarPorHotelTipoYFechaBloqueando(HOTEL, DOBLE, checkIn);
        orden.verify(inventarioRepositorio).buscarPorHotelTipoYFechaBloqueando(HOTEL, "SUITE", checkIn);
        assertEquals(EstadoHold.LIBERADO, anterior.getEstado());
    }

    @Test
    void reemplazarHoldLiberadoNoDevuelveCupoAjenoNiLoVuelveAGuardar() {
        Hold anterior = holdExistente("ant", DOBLE, 2, checkIn, checkOut);
        anterior.liberar();
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 3, 2);
        InventarioDiario n2 = cargarDia(DOBLE, checkIn.plusDays(1), 3, 2);

        // sin credito: libre 1 < 2 => sin disponibilidad (el cupo ya pertenece a otros)
        assertThrows(SinDisponibilidadException.class,
                () -> servicio.reemplazarHold("ant", HOTEL, DOBLE, 2, checkIn, checkOut));
        assertEquals(2, n1.getUnidadesOcupadas());

        // con 1 habitacion alcanza y el cupo ocupado por terceros no se toca
        servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, checkIn, checkOut);
        assertEquals(3, n1.getUnidadesOcupadas());
        assertEquals(3, n2.getUnidadesOcupadas());
        ArgumentCaptor<Hold> cap = ArgumentCaptor.forClass(Hold.class);
        verify(holdRepositorio, times(1)).guardar(cap.capture());
        assertNotEquals("ant", cap.getValue().getId());
    }

    @Test
    void reemplazarHoldConfirmadoLoDejaLiberadoYCreaUnoPendiente() {
        Hold anterior = holdExistente("ant", DOBLE, 1, checkIn, checkIn.plusDays(1));
        anterior.confirmar();
        InventarioDiario n1 = cargarDia(DOBLE, checkIn, 1, 1);

        servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, checkIn, checkIn.plusDays(1));

        assertEquals(EstadoHold.LIBERADO, anterior.getEstado());
        assertEquals(1, n1.getUnidadesOcupadas());
    }

    @Test
    void reemplazarHoldDeOtroHotelLanzaSolicitudInvalida() {
        holdExistente("ant", DOBLE, 1, checkIn, checkOut);
        InventarioTarifasException ex = assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.reemplazarHold("ant", 2L, DOBLE, 1, checkIn, checkOut));
        assertTrue(ex.getMessage().contains("hotelId=1"));
        assertTrue(ex.getMessage().contains("hotelId=2"));
        verifyNoInteractions(inventarioRepositorio);
        verify(holdRepositorio, never()).guardar(any());
    }

    @Test
    void reemplazarHoldInexistenteLanzaHoldNoEncontrado() {
        when(holdRepositorio.buscarPorId("ant")).thenReturn(Optional.empty());
        assertInventario(CodigoErrorInventarioTarifas.HOLD_NO_ENCONTRADO,
                () -> servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, checkIn, checkOut));
        verifyNoInteractions(inventarioRepositorio);
    }

    @Test
    void reemplazarHoldConPedidoInvalidoFallaAntesDeBuscarElHold() {
        assertInventario(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                () -> servicio.reemplazarHold("ant", null, DOBLE, 1, checkIn, checkOut));
        assertThrows(SeguridadException.class,
                () -> servicio.reemplazarHold("ant", HOTEL, DOBLE, 0, checkIn, checkOut));
        verifyNoInteractions(holdRepositorio, inventarioRepositorio);
    }

    @Test
    void reemplazarHoldSinInventarioParaLasNochesNuevasFallaSinModificarNada() {
        Hold anterior = holdExistente("ant", DOBLE, 1, checkIn, checkIn.plusDays(1));
        InventarioDiario vieja = cargarDia(DOBLE, checkIn, 5, 1);
        LocalDate nIn = checkIn.plusDays(20);
        when(inventarioRepositorio.buscarPorHotelTipoYFechaBloqueando(HOTEL, DOBLE, nIn)).thenReturn(Optional.empty());

        SinDisponibilidadException ex = assertThrows(SinDisponibilidadException.class,
                () -> servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, nIn, nIn.plusDays(1)));

        assertTrue(ex.getMessage().contains("No hay inventario cargado"));
        assertEquals(1, vieja.getUnidadesOcupadas());
        assertEquals(EstadoHold.PENDIENTE, anterior.getEstado());
        verify(inventarioRepositorio, never()).guardar(any());
    }

    @Test
    void reemplazarHoldIgnoraNochesAnterioresSinFilaDeInventario() {
        Hold anterior = holdExistente("ant", DOBLE, 1, checkIn, checkIn.plusDays(1));
        when(inventarioRepositorio.buscarPorHotelTipoYFechaBloqueando(HOTEL, DOBLE, checkIn)).thenReturn(Optional.empty());
        LocalDate nIn = checkIn.plusDays(7);
        InventarioDiario nueva = cargarDia(DOBLE, nIn, 2, 0);

        servicio.reemplazarHold("ant", HOTEL, DOBLE, 1, nIn, nIn.plusDays(1));

        assertEquals(1, nueva.getUnidadesOcupadas());
        assertEquals(EstadoHold.LIBERADO, anterior.getEstado());
    }
}
