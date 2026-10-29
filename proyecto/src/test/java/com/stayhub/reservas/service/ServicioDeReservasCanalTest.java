package com.stayhub.reservas.service;

import static com.stayhub.seguridad.IdentidadFixture.campo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stayhub.canalesexternos.contrato.interno.SolicitudReserva;
import com.stayhub.canalesexternos.dto.HuespedDTO;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.contrato.SinDisponibilidadException;
import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.model.EstadoReserva;
import com.stayhub.reservas.model.Huesped;
import com.stayhub.reservas.model.Reserva;
import com.stayhub.reservas.repository.ReservaRepository;
import com.stayhub.seguridad.SeguridadException;
import jakarta.enterprise.inject.Instance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Camino de reservas que llegan desde un canal externo (OTA). */
class ServicioDeReservasCanalTest {
    ServicioDeReservasImpl servicio;
    ReservaRepository repo;
    GestionDeDisponibilidadPort cupo;
    Instance<GestionDeDisponibilidadPort> instancia;
    LocalDate desde = LocalDate.now().plusDays(5), hasta = desde.plusDays(2);

    @SuppressWarnings("unchecked")
    @BeforeEach void preparar() throws Exception {
        servicio = new ServicioDeReservasImpl();
        repo = mock(ReservaRepository.class);
        cupo = mock(GestionDeDisponibilidadPort.class);
        instancia = mock(Instance.class);
        when(instancia.isResolvable()).thenReturn(true);
        when(instancia.get()).thenReturn(cupo);
        campo(servicio, "repositorio", repo);
        campo(servicio, "disponibilidad", instancia);
        when(repo.buscarPorCanalYReferencia(anyString(), anyString())).thenReturn(Optional.empty());
        when(cupo.crearHold(any(), any(), anyInt(), any(), any())).thenReturn("hold-1");
    }

    SolicitudReserva sol() {
        return new SolicitudReserva("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 2,
                new HuespedDTO("Ana", "Paz", "ana@mail.com", "123"), new BigDecimal("1000.50"), "ARS");
    }

    SolicitudReserva sol(UnaryOperator<SolicitudReserva> cambio) { return cambio.apply(sol()); }

    SolicitudReserva con(String ref, String canal, Long hotel, LocalDate in, LocalDate out, String tipo, int cant,
                         HuespedDTO h, BigDecimal precio, String moneda) {
        return new SolicitudReserva(ref, canal, hotel, in, out, tipo, cant, h, precio, moneda);
    }

    Reserva existente(String estado) {
        Reserva r = new Reserva("BOOKING", "REF-1", 1L, "DOBLE", 2, desde, hasta,
                new Huesped("Ana", "Paz", "ana@mail.com", "123"), new BigDecimal("1000.50"), "ARS");
        switch (estado) {
            case "CONFIRMADA" -> r.confirmar("hold-viejo");
            case "CANCELADA" -> { r.iniciarHold("hold-viejo"); r.cancelar(); }
            case "RECHAZADA" -> r.rechazar();
            default -> r.iniciarHold("hold-viejo");
        }
        when(repo.buscarPorCanalYReferencia("BOOKING", "REF-1")).thenReturn(Optional.of(r));
        return r;
    }

    // ---------------- crearDesdeCanal ----------------

    @Test void crearDesdeCanalRetieneConfirmaYGuardaLaReserva() {
        var res = servicio.crearDesdeCanal(sol());
        assertEquals("CONFIRMADA", res.estado());
        verify(cupo).crearHold(1L, "DOBLE", 2, desde, hasta);
        verify(cupo).confirmarHold("hold-1");
        verify(repo).guardar(argThat(r -> r.getEstado() == EstadoReserva.CONFIRMADA
                && "hold-1".equals(r.getHoldId())
                && "BOOKING".equals(r.getCanal()) && "REF-1".equals(r.getReferenciaExterna())
                && new BigDecimal("1000.50").equals(r.getPrecioTotal())));
    }

    @Test void crearDesdeCanalDuplicadoDevuelveElEstadoActualSinTocarCupo() {
        existente("CONFIRMADA");
        var res = servicio.crearDesdeCanal(sol());
        assertEquals("CONFIRMADA", res.estado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void crearDesdeCanalSinDisponibilidadGuardaReservaRechazada() {
        when(cupo.crearHold(any(), any(), anyInt(), any(), any())).thenThrow(new SinDisponibilidadException("sin cupo"));
        var res = servicio.crearDesdeCanal(sol());
        assertEquals("RECHAZADA", res.estado());
        verify(cupo, never()).confirmarHold(any());
        verify(repo).guardar(argThat(r -> r.getEstado() == EstadoReserva.RECHAZADA && r.getHoldId() == null));
    }

    @Test void crearDesdeCanalRechazaSiLaConfirmacionDelHoldFallaPorFaltaDeCupo() {
        doThrow(new SinDisponibilidadException("vencido")).when(cupo).confirmarHold("hold-1");
        var res = servicio.crearDesdeCanal(sol());
        assertEquals("RECHAZADA", res.estado());
        verify(repo).guardar(argThat(r -> r.getEstado() == EstadoReserva.RECHAZADA));
    }

    @Test void crearDesdeCanalSinInventarioDisponibleFallaConDependenciaNoDisponible() {
        when(instancia.isResolvable()).thenReturn(false);
        var error = assertThrows(ReservaException.class, () -> servicio.crearDesdeCanal(sol()));
        assertEquals(CodigoErrorReserva.DEPENDENCIA_NO_DISPONIBLE, error.getCodigo());
        verify(repo, never()).guardar(any());
    }

    @Test void crearDesdeCanalAceptaCheckInDeHoyYPrecioCero() {
        var res = servicio.crearDesdeCanal(con("REF-1", "BOOKING", 1L, LocalDate.now(), LocalDate.now().plusDays(1), "DOBLE", 1,
                new HuespedDTO("Ana", "Paz", "ana@mail.com", null), BigDecimal.ZERO, "ARS"));
        assertEquals("CONFIRMADA", res.estado());
        verify(repo).guardar(any());
    }

    @Test void crearDesdeCanalAceptaLosLimitesSuperioresPermitidos() {
        // 31 noches, 10 habitaciones y precio de 12 dígitos con 2 decimales
        var res = servicio.crearDesdeCanal(con("R".repeat(60), "BOOKING", 1L, desde, desde.plusDays(31), "T".repeat(30), 10,
                new HuespedDTO("Ana", "Paz", "ana@mail.com", "1"), new BigDecimal("9999999999.99"), "ARS"));
        assertEquals("CONFIRMADA", res.estado());
        verify(cupo).crearHold(1L, "T".repeat(30), 10, desde, desde.plusDays(31));
    }

    @Test void solicitudNulaSeRechazaConEstado400() {
        var error = assertThrows(SeguridadException.class, () -> servicio.crearDesdeCanal(null));
        assertEquals(400, error.getEstado());
        assertEquals("Reserva externa inválida", error.getMessage());
        verifyNoInteractions(repo, cupo);
    }

    @Test void checkInEnElPasadoEsSolicitudInvalida() {
        var s = con("REF-1", "BOOKING", 1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "DOBLE", 1,
                new HuespedDTO("Ana", "Paz", "ana@mail.com", "1"), BigDecimal.TEN, "ARS");
        var error = assertThrows(ReservaException.class, () -> servicio.crearDesdeCanal(s));
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void camposObligatoriosFaltantesSonSolicitudInvalida() {
        var h = new HuespedDTO("Ana", "Paz", "ana@mail.com", "1");
        var casos = java.util.List.of(
                con("REF-1", null, 1L, desde, hasta, "DOBLE", 1, h, BigDecimal.TEN, "ARS"),
                con("REF-1", "  ", 1L, desde, hasta, "DOBLE", 1, h, BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", null, desde, hasta, "DOBLE", 1, h, BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, null, BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, null, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, new BigDecimal("-0.01"), "ARS"));
        for (var s : casos) {
            var error = assertThrows(ReservaException.class, () -> servicio.crearDesdeCanal(s), s.toString());
            assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo(), s.toString());
        }
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void limitesDeNegocioExcedidosSeRechazanConEstado400() {
        var h = new HuespedDTO("Ana", "Paz", "ana@mail.com", "1");
        var casos = java.util.List.of(
                con("REF-1", "BOOKING", 1L, desde, desde.plusDays(32), "DOBLE", 1, h, BigDecimal.TEN, "ARS"),   // 32 noches
                con("REF-1", "BOOKING", 1L, desde, desde, "DOBLE", 1, h, BigDecimal.TEN, "ARS"),               // 0 noches
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 0, h, BigDecimal.TEN, "ARS"),               // 0 habitaciones
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 11, h, BigDecimal.TEN, "ARS"),              // 11 habitaciones
                con(null, "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, BigDecimal.TEN, "ARS"),                  // sin referencia
                con("R".repeat(61), "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, null, 1, h, BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "T".repeat(31), 1, h, BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, BigDecimal.TEN, ""),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, BigDecimal.TEN, "ARSX"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, new BigDecimal("1.001"), "ARS"),      // 3 decimales
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, h, new BigDecimal("10000000000.00"), "ARS"), // precisión 13
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, new HuespedDTO(" ", "Paz", "ana@mail.com", "1"), BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, new HuespedDTO("Ana", null, "ana@mail.com", "1"), BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, new HuespedDTO("Ana", "Paz", "sin-arroba", "1"), BigDecimal.TEN, "ARS"),
                con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, new HuespedDTO("Ana", "Paz", "ana@mail.com", "9".repeat(31)), BigDecimal.TEN, "ARS"));
        int i = 0;
        for (var s : casos) {
            var error = assertThrows(SeguridadException.class, () -> servicio.crearDesdeCanal(s), "caso " + i + ": " + s);
            assertEquals(400, error.getEstado(), "caso " + i);
            i++;
        }
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    // ---------------- modificarDesdeCanal ----------------

    @Test void modificarDesdeCanalReemplazaElHoldYActualizaLosDatos() {
        Reserva r = existente("CONFIRMADA");
        when(cupo.reemplazarHold("hold-viejo", 1L, "SUITE", 3, desde.plusDays(1), hasta.plusDays(3))).thenReturn("hold-nuevo");
        var s = con("REF-1", "BOOKING", 1L, desde.plusDays(1), hasta.plusDays(3), "SUITE", 3,
                new HuespedDTO("Ana", "Paz", "ana@mail.com", "1"), new BigDecimal("2500.00"), "ARS");
        var res = servicio.modificarDesdeCanal(s);
        assertEquals("CONFIRMADA", res.estado());
        assertEquals("hold-nuevo", r.getHoldId());
        assertEquals("SUITE", r.getTipoHabitacion());
        assertEquals(3, r.getCantidadHabitaciones());
        assertEquals(desde.plusDays(1), r.getCheckIn());
        assertEquals(hasta.plusDays(3), r.getCheckOut());
        assertEquals(new BigDecimal("2500.00"), r.getPrecioTotal());
        verify(cupo).confirmarHold("hold-nuevo");
        verify(cupo, never()).crearHold(any(), any(), anyInt(), any(), any());
        verify(cupo, never()).liberarHold(any());
        verify(repo).guardar(r);
    }

    @Test void modificarDesdeCanalSinDisponibilidadNoCambiaNadaYLanzaSinDisponibilidad() {
        Reserva r = existente("CONFIRMADA");
        when(cupo.reemplazarHold(any(), any(), any(), anyInt(), any(), any())).thenThrow(new SinDisponibilidadException("lleno"));
        var error = assertThrows(ReservaException.class, () -> servicio.modificarDesdeCanal(
                con("REF-1", "BOOKING", 1L, desde, hasta, "SUITE", 5, new HuespedDTO("A", "B", "a@b.co", "1"), BigDecimal.TEN, "ARS")));
        assertEquals(CodigoErrorReserva.SIN_DISPONIBILIDAD, error.getCodigo());
        assertTrue(error.getMessage().contains("canal=BOOKING") && error.getMessage().contains("referenciaExterna=REF-1"));
        assertInstanceOf(SinDisponibilidadException.class, error.getCause());
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        assertEquals("hold-viejo", r.getHoldId());
        assertEquals("DOBLE", r.getTipoHabitacion());
        verify(cupo, never()).confirmarHold(any());
        verify(repo, never()).guardar(any());
    }

    @Test void modificarDesdeCanalSobreReservaInexistenteEsNoEncontrada() {
        var error = assertThrows(ReservaException.class, () -> servicio.modificarDesdeCanal(sol()));
        assertEquals(CodigoErrorReserva.RESERVA_NO_ENCONTRADA, error.getCodigo());
        assertTrue(error.getMessage().contains("canal=BOOKING referenciaExterna=REF-1"));
        verifyNoInteractions(cupo);
    }

    @Test void modificarDesdeCanalNoRevivaUnaReservaCancelada() {
        Reserva r = existente("CANCELADA");
        var res = servicio.modificarDesdeCanal(sol());
        assertEquals("CANCELADA", res.estado());
        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void modificarDesdeCanalSobreRechazadaSinHoldHaceUnIntentoNuevoYConfirma() {
        Reserva r = existente("RECHAZADA");
        var res = servicio.modificarDesdeCanal(sol());
        assertEquals("CONFIRMADA", res.estado());
        assertEquals("hold-1", r.getHoldId());
        assertEquals(2, r.getCantidadHabitaciones());
        verify(cupo, never()).reemplazarHold(any(), any(), any(), anyInt(), any(), any());
        verify(cupo).confirmarHold("hold-1");
        verify(repo).guardar(r);
    }

    @Test void modificarDesdeCanalSobreRechazadaSigueRechazadaSiTampocoHayCupo() {
        Reserva r = existente("RECHAZADA");
        when(cupo.crearHold(any(), any(), anyInt(), any(), any())).thenThrow(new SinDisponibilidadException("lleno"));
        var res = servicio.modificarDesdeCanal(sol());
        assertEquals("RECHAZADA", res.estado());
        assertEquals(EstadoReserva.RECHAZADA, r.getEstado());
        verify(repo).guardar(r);
    }

    @Test void modificarDesdeCanalValidaLaSolicitudAntesDeBuscar() {
        var s = con("REF-1", "BOOKING", 1L, desde, hasta, "DOBLE", 1, null, BigDecimal.TEN, "ARS");
        var error = assertThrows(ReservaException.class, () -> servicio.modificarDesdeCanal(s));
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
        verify(repo, never()).buscarPorCanalYReferencia(any(), any());
    }

    // ---------------- cancelarDesdeCanal ----------------

    @Test void cancelarDesdeCanalLiberaElHoldYCancela() {
        Reserva r = existente("CONFIRMADA");
        var res = servicio.cancelarDesdeCanal("BOOKING", "REF-1");
        assertEquals("CANCELADA", res.estado());
        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        verify(cupo).liberarHold("hold-viejo");
        verify(repo).guardar(r);
    }

    @Test void cancelarDesdeCanalYaCanceladaNoLiberaDeNuevo() {
        existente("CANCELADA");
        var res = servicio.cancelarDesdeCanal("BOOKING", "REF-1");
        assertEquals("CANCELADA", res.estado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void cancelarDesdeCanalSinHoldNoLlamaALiberar() {
        Reserva r = existente("RECHAZADA");
        var res = servicio.cancelarDesdeCanal("BOOKING", "REF-1");
        assertEquals("CANCELADA", res.estado());
        verify(cupo, never()).liberarHold(any());
        verify(repo).guardar(r);
    }

    @Test void cancelarDesdeCanalInexistenteEsNoEncontrada() {
        var error = assertThrows(ReservaException.class, () -> servicio.cancelarDesdeCanal("EXPEDIA", "X-1"));
        assertEquals(CodigoErrorReserva.RESERVA_NO_ENCONTRADA, error.getCodigo());
        assertTrue(error.getMessage().contains("canal=EXPEDIA referenciaExterna=X-1"));
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void cancelarDesdeCanalSinInventarioDisponibleFallaYNoCancela() {
        Reserva r = existente("CONFIRMADA");
        when(instancia.isResolvable()).thenReturn(false);
        var error = assertThrows(ReservaException.class, () -> servicio.cancelarDesdeCanal("BOOKING", "REF-1"));
        assertEquals(CodigoErrorReserva.DEPENDENCIA_NO_DISPONIBLE, error.getCodigo());
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        verify(repo, never()).guardar(any());
    }
}
