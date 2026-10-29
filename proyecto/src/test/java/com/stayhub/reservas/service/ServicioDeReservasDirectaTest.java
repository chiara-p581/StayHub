package com.stayhub.reservas.service;

import static com.stayhub.seguridad.IdentidadFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.contrato.SinDisponibilidadException;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.model.EstadoReserva;
import com.stayhub.reservas.model.Huesped;
import com.stayhub.reservas.model.Reserva;
import com.stayhub.reservas.repository.ReservaRepository;
import com.stayhub.seguridad.SeguridadException;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.UsuarioResponse;
import jakarta.enterprise.inject.Instance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Camino de reservas directas en StayHub (huésped, ADMIN y PMS). */
class ServicioDeReservasDirectaTest {
    ServicioDeReservasImpl servicio;
    ReservaRepository repo;
    PagoRepository pagos;
    GestionDeDisponibilidadPort cupo;
    ServicioDeUsuarios usuarios;
    ServicioDeInventarioYTarifas tarifas;
    Instance<GestionDeDisponibilidadPort> instancia;
    LocalDate desde = LocalDate.now().plusDays(1), hasta = desde.plusDays(2);

    @SuppressWarnings("unchecked")
    @BeforeEach void preparar() throws Exception {
        servicio = new ServicioDeReservasImpl();
        repo = mock(ReservaRepository.class);
        pagos = mock(PagoRepository.class);
        cupo = mock(GestionDeDisponibilidadPort.class);
        usuarios = mock(ServicioDeUsuarios.class);
        tarifas = mock(ServicioDeInventarioYTarifas.class);
        instancia = mock(Instance.class);
        when(instancia.isResolvable()).thenReturn(true);
        when(instancia.get()).thenReturn(cupo);
        campo(servicio, "repositorio", repo);
        campo(servicio, "pagos", pagos);
        campo(servicio, "usuarios", usuarios);
        campo(servicio, "tarifas", tarifas);
        campo(servicio, "disponibilidad", instancia);
        campo(servicio, "identidad", usuario(1L, "HUESPED"));
        when(cupo.crearHold(any(), any(), anyInt(), any(), any())).thenReturn("hold-1");
        when(usuarios.buscarPorId(1L)).thenReturn(new UsuarioResponse(1L, "real@mail.com", "Real", "Cuenta", "HUESPED"));
        when(tarifas.consultarTarifas(1L, desde, hasta))
                .thenReturn(List.of(new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("150000"), "ARS")));
    }

    ReservaRequest pedido(Long hotel, String tipo, int cant, LocalDate in, LocalDate out) {
        return new ReservaRequest(hotel, tipo, cant, in, out, null, null, null, "123", null, null);
    }

    ReservaRequest cambio(String tipo, int cant, LocalDate in, LocalDate out, BigDecimal precio, String moneda) {
        return new ReservaRequest(1L, tipo, cant, in, out, null, null, null, null, precio, moneda);
    }

    Reserva reserva(long owner, String hold) throws Exception {
        Reserva r = new Reserva("DIRECTA", null, 1L, "DOBLE", 1, desde, hasta,
                new Huesped("Real", "Cuenta", "real@mail.com", "123"), new BigDecimal("150000"), "ARS");
        r.asignarPropietario(owner);
        if (hold != null) r.iniciarHold(hold);
        var f = Reserva.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, 7L);
        when(repo.buscarPorId(7L)).thenReturn(Optional.of(r));
        when(repo.buscarPorIdBloqueando(7L)).thenReturn(Optional.of(r));
        return r;
    }

    void ponerFechaCreacion(Reserva r, LocalDateTime fecha) throws Exception {
        var f = Reserva.class.getDeclaredField("fechaCreacion"); f.setAccessible(true); f.set(r, fecha);
    }

    // ---------------- crearReserva ----------------

    @Test void crearReservaConCupoQuedaPendienteConHoldYGuardada() {
        var res = servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta));
        assertEquals("PENDIENTE", res.estado());
        assertEquals("DIRECTA", res.canal());
        assertNull(res.referenciaExterna());
        assertEquals(new BigDecimal("150000"), res.precioTotal());
        InOrder orden = inOrder(repo, cupo);
        orden.verify(repo).bloquearUsuario(1L);
        orden.verify(cupo).crearHold(1L, "DOBLE", 1, desde, hasta);
        orden.verify(repo).guardar(argThat(r -> "hold-1".equals(r.getHoldId()) && r.getEstado() == EstadoReserva.PENDIENTE));
    }

    @Test void crearReservaSinCupoGuardaReservaRechazada() {
        when(cupo.crearHold(any(), any(), anyInt(), any(), any())).thenThrow(new SinDisponibilidadException("lleno"));
        var res = servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta));
        assertEquals("RECHAZADA", res.estado());
        verify(repo).guardar(argThat(r -> r.getEstado() == EstadoReserva.RECHAZADA && r.getHoldId() == null));
    }

    @Test void crearReservaSinInventarioDisponibleFallaYNoGuarda() {
        when(instancia.isResolvable()).thenReturn(false);
        var error = assertThrows(ReservaException.class, () -> servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta)));
        assertEquals(CodigoErrorReserva.DEPENDENCIA_NO_DISPONIBLE, error.getCodigo());
        verify(repo, never()).guardar(any());
    }

    @Test void crearReservaConNuevePendientesTodaviaEsPosible() {
        when(repo.contarPendientesPorUsuario(1L)).thenReturn(9L);
        assertEquals("PENDIENTE", servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta)).estado());
    }

    @Test void crearReservaConDiezPendientesSeBloqueaConEstado429() {
        when(repo.contarPendientesPorUsuario(1L)).thenReturn(10L);
        var error = assertThrows(SeguridadException.class, () -> servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta)));
        assertEquals(429, error.getEstado());
        verify(repo).bloquearUsuario(1L);
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void crearReservaExigeRolHuesped() throws Exception {
        campo(servicio, "identidad", usuario(9L, "ADMIN"));
        var error = assertThrows(SeguridadException.class, () -> servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta)));
        assertEquals(403, error.getEstado());
        verifyNoInteractions(cupo, repo);
    }

    @Test void crearReservaSinSesionEs401() throws Exception {
        campo(servicio, "identidad", usuario(null, null));
        var error = assertThrows(SeguridadException.class, () -> servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta)));
        assertEquals(401, error.getEstado());
        verifyNoInteractions(cupo, repo);
    }

    @Test void crearReservaConSolicitudInvalidaEs400() {
        var casos = List.of(
                pedido(null, "DOBLE", 1, desde, hasta),
                pedido(0L, "DOBLE", 1, desde, hasta),
                pedido(-3L, "DOBLE", 1, desde, hasta),
                pedido(1L, null, 1, desde, hasta),
                pedido(1L, "T".repeat(31), 1, desde, hasta),
                pedido(1L, "DOBLE", 0, desde, hasta),
                pedido(1L, "DOBLE", 11, desde, hasta),
                pedido(1L, "DOBLE", 1, null, hasta),
                pedido(1L, "DOBLE", 1, desde, desde),
                pedido(1L, "DOBLE", 1, desde, desde.plusDays(32)),
                pedido(1L, "DOBLE", 1, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)),
                new ReservaRequest(1L, "DOBLE", 1, desde, hasta, null, null, null, null, null, null));   // sin teléfono
        int i = 0;
        for (var s : casos) {
            var error = assertThrows(SeguridadException.class, () -> servicio.crearReserva(s), "caso " + i);
            assertEquals(400, error.getEstado(), "caso " + i);
            i++;
        }
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.crearReserva(null)).getEstado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void crearReservaSinTarifaParaElTipoEs409() {
        var error = assertThrows(SeguridadException.class, () -> servicio.crearReserva(pedido(1L, "SUITE", 1, desde, hasta)));
        assertEquals(409, error.getEstado());
        verifyNoInteractions(cupo);
    }

    @Test void crearReservaConTotalFueraDeLimitesEs400() {
        when(tarifas.consultarTarifas(1L, desde, hasta))
                .thenReturn(List.of(new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("999999999999"), "ARS")));
        var error = assertThrows(SeguridadException.class, () -> servicio.crearReserva(pedido(1L, "DOBLE", 2, desde, hasta)));
        assertEquals(400, error.getEstado());
        verify(repo, never()).guardar(any());
    }

    @Test void crearReservaConTarifaDeTresDecimalesEs400() {
        when(tarifas.consultarTarifas(1L, desde, hasta))
                .thenReturn(List.of(new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("10.001"), "ARS")));
        var error = assertThrows(SeguridadException.class, () -> servicio.crearReserva(pedido(1L, "DOBLE", 1, desde, hasta)));
        assertEquals(400, error.getEstado());
    }

    @Test void crearReservaTomaLaPrimeraTarifaDelTipoPedido() {
        when(tarifas.consultarTarifas(1L, desde, hasta)).thenReturn(List.of(
                new TarifaDTO(1L, "SUITE", desde, hasta, new BigDecimal("1"), "USD"),
                new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("20.50"), "ARS")));
        var res = servicio.crearReserva(pedido(1L, "DOBLE", 3, desde, hasta));
        assertEquals(new BigDecimal("61.50"), res.precioTotal());
        assertEquals("ARS", res.moneda());
    }

    // ---------------- consultarReserva ----------------

    @Test void consultarReservaPropiaDevuelveLosDatos() throws Exception {
        reserva(1, "hold-1");
        var res = servicio.consultarReserva(7L);
        assertEquals(7L, res.id());
        assertEquals("PENDIENTE", res.estado());
        assertEquals("real@mail.com", res.huespedEmail());
    }

    @Test void consultarReservaConIdInvalidoOInexistenteEs404() {
        for (Long id : new Long[]{null, 0L, -1L, 99L}) {
            var error = assertThrows(SeguridadException.class, () -> servicio.consultarReserva(id), String.valueOf(id));
            assertEquals(404, error.getEstado());
            assertEquals("Reserva no disponible", error.getMessage());
        }
    }

    @Test void consultarReservaConIdUnoEsValidoPeroInexistenteEs404() {
        // límite inferior: el id 1 supera la validación y llega al repositorio
        assertThrows(SeguridadException.class, () -> servicio.consultarReserva(1L));
        verify(repo).buscarPorId(1L);
    }

    @Test void adminPuedeConsultarReservaAjena() throws Exception {
        reserva(2, "hold-1");
        campo(servicio, "identidad", usuario(9L, "ADMIN"));
        assertEquals(7L, servicio.consultarReserva(7L).id());
    }

    // ---------------- confirmarReserva ----------------

    @Test void confirmarReservaConIdInvalidoEs404() {
        for (Long id : new Long[]{null, 0L, -5L}) {
            assertEquals(404, assertThrows(SeguridadException.class, () -> servicio.confirmarReserva(id)).getEstado());
        }
        verifyNoInteractions(cupo);
    }

    @Test void confirmarReservaYaConfirmadaEsIdempotente() throws Exception {
        Reserva r = reserva(1, "hold-1");
        r.confirmar("hold-1");
        when(pagos.existeAprobadoParaReserva(7L)).thenReturn(true);
        assertEquals("CONFIRMADA", servicio.confirmarReserva(7L).estado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void confirmarReservaCanceladaConPagoEsTransicionInvalida() throws Exception {
        Reserva r = reserva(1, "hold-1");
        r.cancelar();
        when(pagos.existeAprobadoParaReserva(7L)).thenReturn(true);
        var error = assertThrows(ReservaException.class, () -> servicio.confirmarReserva(7L));
        assertEquals(CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA, error.getCodigo());
        assertTrue(error.getMessage().contains("CANCELADA"));
        verifyNoInteractions(cupo);
        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
    }

    @Test void confirmarReservaPendienteConfirmaHoldYGuarda() throws Exception {
        Reserva r = reserva(1, "hold-1");
        when(pagos.existeAprobadoParaReserva(7L)).thenReturn(true);
        servicio.confirmarReserva(7L);
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        verify(cupo).confirmarHold("hold-1");
        verify(repo).guardar(r);
    }

    // ---------------- cancelarReserva ----------------

    @Test void cancelarReservaLiberaElHoldYGuarda() throws Exception {
        Reserva r = reserva(1, "hold-1");
        assertEquals("CANCELADA", servicio.cancelarReserva(7L).estado());
        verify(cupo).liberarHold("hold-1");
        verify(repo).guardar(r);
    }

    @Test void cancelarReservaYaCanceladaNoLiberaNiGuardaDeNuevo() throws Exception {
        Reserva r = reserva(1, "hold-1");
        r.cancelar();
        assertEquals("CANCELADA", servicio.cancelarReserva(7L).estado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void cancelarReservaSinHoldNoLlamaALiberar() throws Exception {
        reserva(1, null);
        assertEquals("CANCELADA", servicio.cancelarReserva(7L).estado());
        verify(cupo, never()).liberarHold(any());
    }

    @Test void cancelarReservaInexistenteEs404() {
        assertEquals(404, assertThrows(SeguridadException.class, () -> servicio.cancelarReserva(55L)).getEstado());
    }

    // ---------------- modificarReserva ----------------

    @Test void modificarReservaPendienteConHoldReemplazaElHoldYRecalculaElPrecio() throws Exception {
        Reserva r = reserva(1, "hold-1");
        LocalDate nuevoOut = desde.plusDays(3);
        when(tarifas.consultarTarifas(1L, desde, nuevoOut))
                .thenReturn(List.of(new TarifaDTO(1L, "DOBLE", desde, nuevoOut, new BigDecimal("100.00"), "ARS")));
        when(cupo.reemplazarHold("hold-1", 1L, "DOBLE", 2, desde, nuevoOut)).thenReturn("hold-2");
        var res = servicio.modificarReserva(7L, cambio("DOBLE", 2, desde, nuevoOut, BigDecimal.ONE, "ARS"));
        assertEquals("PENDIENTE", res.estado());
        assertEquals(new BigDecimal("200.00"), res.precioTotal());   // el precio del request no es autoridad
        assertEquals(2, res.cantidadHabitaciones());
        assertEquals(nuevoOut, res.checkOut());
        assertEquals("hold-2", r.getHoldId());
        verify(cupo).reemplazarHold("hold-1", 1L, "DOBLE", 2, desde, nuevoOut);
        verify(repo).guardar(r);
    }

    @Test void modificarReservaSinDisponibilidadSeMantieneSinCambios() throws Exception {
        Reserva r = reserva(1, "hold-1");
        when(cupo.reemplazarHold(any(), any(), any(), anyInt(), any(), any())).thenThrow(new SinDisponibilidadException("lleno"));
        var error = assertThrows(ReservaException.class,
                () -> servicio.modificarReserva(7L, cambio("DOBLE", 2, desde, hasta, BigDecimal.ONE, "ARS")));
        assertEquals(CodigoErrorReserva.SIN_DISPONIBILIDAD, error.getCodigo());
        assertInstanceOf(SinDisponibilidadException.class, error.getCause());
        assertEquals(1, r.getCantidadHabitaciones());
        assertEquals("hold-1", r.getHoldId());
        assertEquals(new BigDecimal("150000"), r.getPrecioTotal());
        verify(repo, never()).guardar(any());
    }

    @Test void modificarReservaSinHoldPrevioIniciaUnHoldNuevo() throws Exception {
        Reserva r = reserva(1, null);
        var res = servicio.modificarReserva(7L, cambio("DOBLE", 2, desde, hasta, BigDecimal.ONE, "ARS"));
        assertEquals("PENDIENTE", res.estado());
        assertEquals("hold-1", r.getHoldId());
        assertEquals(new BigDecimal("300000"), r.getPrecioTotal());
        verify(cupo, never()).reemplazarHold(any(), any(), any(), anyInt(), any(), any());
        verify(repo).guardar(r);
    }

    @Test void modificarReservaSinHoldYSinCupoQuedaRechazada() throws Exception {
        Reserva r = reserva(1, null);
        when(cupo.crearHold(any(), any(), anyInt(), any(), any())).thenThrow(new SinDisponibilidadException("lleno"));
        assertEquals("RECHAZADA", servicio.modificarReserva(7L, cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, "ARS")).estado());
        assertEquals(EstadoReserva.RECHAZADA, r.getEstado());
    }

    @Test void modificarReservaCanceladaORechazadaEsTransicionInvalida() throws Exception {
        Reserva r = reserva(1, "hold-1");
        r.cancelar();
        var e1 = assertThrows(ReservaException.class, () -> servicio.modificarReserva(7L, cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, "ARS")));
        assertEquals(CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA, e1.getCodigo());
        assertTrue(e1.getMessage().contains("CANCELADA"));
        r.rechazar();
        var e2 = assertThrows(ReservaException.class, () -> servicio.modificarReserva(7L, cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, "ARS")));
        assertEquals(CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA, e2.getCodigo());
        assertTrue(e2.getMessage().contains("RECHAZADA"));
        verifyNoInteractions(cupo);
    }

    @Test void modificarReservaConPagoAprobadoEs409() throws Exception {
        reserva(1, "hold-1");
        when(pagos.existeAprobadoParaReserva(7L)).thenReturn(true);
        var error = assertThrows(SeguridadException.class, () -> servicio.modificarReserva(7L, cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, "ARS")));
        assertEquals(409, error.getEstado());
        verifyNoInteractions(cupo);
    }

    @Test void modificarReservaConCambioDeMonedaEs409() throws Exception {
        reserva(1, "hold-1");
        when(tarifas.consultarTarifas(1L, desde, hasta))
                .thenReturn(List.of(new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("10"), "USD")));
        var error = assertThrows(SeguridadException.class, () -> servicio.modificarReserva(7L, cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, "ARS")));
        assertEquals(409, error.getEstado());
        assertEquals("La modificación no puede cambiar de moneda", error.getMessage());
        verifyNoInteractions(cupo);
    }

    @Test void modificarReservaConSolicitudNulaEs400() throws Exception {
        reserva(1, "hold-1");
        var error = assertThrows(SeguridadException.class, () -> servicio.modificarReserva(7L, null));
        assertEquals(400, error.getEstado());
    }

    @Test void modificarReservaConDatosInvalidosEsSolicitudInvalidaOLimite() throws Exception {
        reserva(1, "hold-1");
        var invalidas = List.of(
                cambio("DOBLE", 1, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BigDecimal.ONE, "ARS"),
                cambio("DOBLE", 1, desde, hasta, null, "ARS"),
                cambio("DOBLE", 1, desde, hasta, new BigDecimal("-1"), "ARS"),
                cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, null),
                cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, " "));
        for (var s : invalidas) {
            var error = assertThrows(ReservaException.class, () -> servicio.modificarReserva(7L, s), s.toString());
            assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo(), s.toString());
        }
        var limites = List.of(
                cambio("DOBLE", 0, desde, hasta, BigDecimal.ONE, "ARS"),
                cambio(" ", 1, desde, hasta, BigDecimal.ONE, "ARS"),
                cambio("DOBLE", 1, desde, desde, BigDecimal.ONE, "ARS"));
        for (var s : limites) {
            assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.modificarReserva(7L, s), s.toString()).getEstado());
        }
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void modificarReservaAjenaEs404() throws Exception {
        reserva(2, "hold-1");
        assertEquals(404, assertThrows(SeguridadException.class,
                () -> servicio.modificarReserva(7L, cambio("DOBLE", 1, desde, hasta, BigDecimal.ONE, "ARS"))).getEstado());
        verifyNoInteractions(cupo);
    }

    // ---------------- listados ----------------

    @Test void listarPorHotelComoAdminUsaPrimeraPaginaDeCien() throws Exception {
        Reserva r = reserva(1, "hold-1");
        campo(servicio, "identidad", usuario(9L, "ADMIN"));
        when(repo.listarPorHotel(1L, 0, 100)).thenReturn(List.of(r));
        var lista = servicio.listarPorHotel(1L);
        assertEquals(1, lista.size());
        assertEquals(7L, lista.get(0).id());
    }

    @Test void listarPorHotelComoHuespedEs403() {
        var error = assertThrows(SeguridadException.class, () -> servicio.listarPorHotel(1L));
        assertEquals(403, error.getEstado());
        verify(repo, never()).listarPorHotel(anyLong(), anyInt(), anyInt());
    }

    @Test void listarPorHotelComoPmsSoloEnSusHoteles() throws Exception {
        campo(servicio, "identidad", pms(Set.of(1L)));
        when(repo.listarPorHotel(1L, 5, 10)).thenReturn(List.of());
        assertTrue(servicio.listarPorHotel(1L, 5, 10).isEmpty());
        assertEquals(403, assertThrows(SeguridadException.class, () -> servicio.listarPorHotel(2L, 0, 10)).getEstado());
        verify(repo, never()).listarPorHotel(eq(2L), anyInt(), anyInt());
    }

    @Test void paginacionFueraDeRangoEs400YEnLosBordesFunciona() throws Exception {
        campo(servicio, "identidad", usuario(9L, "ADMIN"));
        int[][] malas = {{-1, 10}, {10001, 10}, {0, 0}, {0, 101}};
        for (int[] p : malas) {
            assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.listarPorHotel(1L, p[0], p[1])).getEstado());
        }
        int[][] buenas = {{0, 1}, {10000, 100}};
        for (int[] p : buenas) servicio.listarPorHotel(1L, p[0], p[1]);
        verify(repo).listarPorHotel(1L, 0, 1);
        verify(repo).listarPorHotel(1L, 10000, 100);
        verify(repo, times(2)).listarPorHotel(anyLong(), anyInt(), anyInt());
    }

    @Test void listarPorHuespedEmailListaLasReservasDeLaSesionIgnorandoElEmail() throws Exception {
        // Comportamiento actual (sospechoso, ver reporte): el parámetro email no se usa;
        // se devuelven siempre las reservas del usuario de la sesión.
        Reserva r = reserva(1, "hold-1");
        when(repo.listarPorUsuario(1L, 0, 100)).thenReturn(List.of(r));
        var lista = servicio.listarPorHuespedEmail("otro@mail.com");
        assertEquals(1, lista.size());
        verify(repo).listarPorUsuario(1L, 0, 100);
    }

    @Test void listarLlegadasComoPmsMapeaLasReservas() throws Exception {
        Reserva r = reserva(1, "hold-1");
        campo(servicio, "identidad", pms(Set.of(1L)));
        when(repo.listarLlegadas(1L, desde)).thenReturn(List.of(r));
        var lista = servicio.listarLlegadas(1L, desde);
        assertEquals(1, lista.size());
        assertEquals("PENDIENTE", lista.get(0).estado());
    }

    @Test void listarLlegadasSinPmsEs403() {
        assertEquals(403, assertThrows(SeguridadException.class, () -> servicio.listarLlegadas(1L, desde)).getEstado());
        verify(repo, never()).listarLlegadas(any(), any());
    }

    @Test void listarLlegadasDeHotelNoAutorizadoEs403() throws Exception {
        campo(servicio, "identidad", pms(Set.of(3L)));
        assertEquals(403, assertThrows(SeguridadException.class, () -> servicio.listarLlegadas(1L, desde)).getEstado());
        verify(repo, never()).listarLlegadas(any(), any());
    }

    // ---------------- consultarParaPago ----------------

    @Test void consultarParaPagoDeReservaPendienteRecienteDevuelveLaReserva() throws Exception {
        reserva(1, "hold-1");
        var res = servicio.consultarParaPago(7L);
        assertEquals(7L, res.id());
        assertEquals("PENDIENTE", res.estado());
    }

    @Test void consultarParaPagoDeReservaVencidaEs409() throws Exception {
        Reserva r = reserva(1, "hold-1");
        ponerFechaCreacion(r, LocalDateTime.now().minusMinutes(31));
        var error = assertThrows(SeguridadException.class, () -> servicio.consultarParaPago(7L));
        assertEquals(409, error.getEstado());
    }

    @Test void consultarParaPagoDeReservaDe29MinutosTodaviaVale() throws Exception {
        Reserva r = reserva(1, "hold-1");
        ponerFechaCreacion(r, LocalDateTime.now().minusMinutes(29));
        assertEquals("PENDIENTE", servicio.consultarParaPago(7L).estado());
    }

    @Test void consultarParaPagoDeReservaCanceladaEs409() throws Exception {
        Reserva r = reserva(1, "hold-1");
        r.cancelar();
        assertEquals(409, assertThrows(SeguridadException.class, () -> servicio.consultarParaPago(7L)).getEstado());
    }

    @Test void consultarParaPagoExigeHuesped() throws Exception {
        reserva(1, "hold-1");
        campo(servicio, "identidad", usuario(9L, "ADMIN"));
        assertEquals(403, assertThrows(SeguridadException.class, () -> servicio.consultarParaPago(7L)).getEstado());
    }

    // ---------------- liberarPendientesVencidas ----------------

    @Test void liberarPendientesVencidasCancelaLasPendientesSinPago() throws Exception {
        Reserva r = reserva(1, "hold-1");
        when(repo.pendientesVencidas(any())).thenReturn(List.of(r));
        servicio.liberarPendientesVencidas();
        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        verify(cupo).liberarHold("hold-1");
        verify(repo).guardar(r);
        verify(repo).pendientesVencidas(argThat(t -> t.isBefore(LocalDateTime.now().minusMinutes(29))
                && t.isAfter(LocalDateTime.now().minusMinutes(31))));
    }

    @Test void liberarPendientesVencidasRespetaLasQueTienenPagoAprobado() throws Exception {
        Reserva r = reserva(1, "hold-1");
        when(repo.pendientesVencidas(any())).thenReturn(List.of(r));
        when(pagos.existeAprobadoParaReserva(7L)).thenReturn(true);
        servicio.liberarPendientesVencidas();
        assertEquals(EstadoReserva.PENDIENTE, r.getEstado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void liberarPendientesVencidasIgnoraLasQueYaNoEstanPendientes() throws Exception {
        Reserva r = reserva(1, "hold-1");
        r.confirmar("hold-1");
        when(repo.pendientesVencidas(any())).thenReturn(List.of(r));
        servicio.liberarPendientesVencidas();
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test void liberarPendientesVencidasSinCandidatasNoHaceNada() {
        when(repo.pendientesVencidas(any())).thenReturn(List.of());
        servicio.liberarPendientesVencidas();
        verifyNoInteractions(cupo, pagos);
        verify(repo, never()).guardar(any());
    }

    @Test void liberarPendientesVencidasSinHoldCancelaSinLiberar() throws Exception {
        Reserva r = reserva(1, null);
        when(repo.pendientesVencidas(any())).thenReturn(List.of(r));
        servicio.liberarPendientesVencidas();
        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        verify(cupo, never()).liberarHold(any());
        verify(repo).guardar(r);
    }
}
