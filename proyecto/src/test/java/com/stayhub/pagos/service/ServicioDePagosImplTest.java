package com.stayhub.pagos.service;

import com.stayhub.pagos.client.pasarela.PasarelaDePagoClient;
import com.stayhub.pagos.client.pasarela.ResultadoCobro;
import com.stayhub.pagos.dto.PagoLoteRequest;
import com.stayhub.pagos.dto.PagoRequest;
import com.stayhub.pagos.dto.PagoResponse;
import com.stayhub.pagos.exception.CodigoErrorPago;
import com.stayhub.pagos.exception.PagoException;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import com.stayhub.pagos.messaging.PublicadorEventoPago;
import com.stayhub.pagos.model.EstadoPago;
import com.stayhub.pagos.model.Pago;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.service.ServicioDeReservas;
import com.stayhub.seguridad.IdentidadActual;
import com.stayhub.seguridad.SeguridadException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de ServicioDePagosImpl: solo se mockean sus colaboradores. */
class ServicioDePagosImplTest {

    private static final String CLAVE_VALIDA = "clave-idempotencia-0001";

    private ServicioDePagosImpl servicio;
    private IdentidadActual identidad;
    private PagoRepository repositorio;
    private PasarelaDePagoClient pasarela;
    private PublicadorEventoPago publicador;
    private ServicioDeReservas reservas;

    @BeforeEach
    void preparar() throws Exception {
        servicio = new ServicioDePagosImpl();
        identidad = mock(IdentidadActual.class);
        repositorio = mock(PagoRepository.class);
        pasarela = mock(PasarelaDePagoClient.class);
        publicador = mock(PublicadorEventoPago.class);
        reservas = mock(ServicioDeReservas.class);
        inyectar("identidad", identidad);
        inyectar("repositorio", repositorio);
        inyectar("pasarela", pasarela);
        inyectar("publicadorEventos", publicador);
        inyectar("reservas", reservas);
        when(identidad.usuarioId()).thenReturn(1L);
    }

    private void inyectar(String campo, Object valor) throws Exception {
        var f = ServicioDePagosImpl.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(servicio, valor);
    }

    private static Pago conId(Pago pago, long id) throws Exception {
        var f = Pago.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(pago, id);
        return pago;
    }

    private static ReservaResponse reserva(long id, String estado, String precio, String moneda) {
        return new ReservaResponse(id, "DIRECTA", null, 5L, "DOBLE", 1, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 3), "Ana", "Paz", "ana@test.com", new BigDecimal(precio), moneda,
                estado, LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    private void reservaPendiente(long id, String precio, String moneda) {
        when(reservas.consultarParaPago(id)).thenReturn(reserva(id, "PENDIENTE", precio, moneda));
        when(reservas.confirmarReserva(id)).thenReturn(reserva(id, "CONFIRMADA", precio, moneda));
    }

    private void pasarelaAprueba() {
        when(pasarela.cobrar(any(), anyString(), anyString())).thenReturn(new ResultadoCobro(true, "REF-9", null));
    }

    // ---------------------------------------------------------------- procesarPago

    @Test
    void pagoAprobadoConfirmaLaReservaYPublicaElEvento() {
        reservaPendiente(7, "100.00", "ARS");
        pasarelaAprueba();

        PagoResponse r = servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100.00"), "ARS"));

        assertEquals("APROBADO", r.estado());
        assertEquals("REF-9", r.referenciaPasarela());
        assertEquals(7L, r.reservaId());
        assertEquals(List.of(7L), r.reservaIds());
        assertEquals(new BigDecimal("100.00"), r.monto());
        assertEquals("ARS", r.moneda());
        verify(pasarela).cobrar(new BigDecimal("100.00"), "ARS", "reserva-7");
        verify(repositorio).bloquearReservas(List.of(7L));
        verify(repositorio, times(2)).guardar(any(Pago.class));
        verify(reservas).confirmarReserva(7L);
        ArgumentCaptor<EventoPagoAprobado> evento = ArgumentCaptor.forClass(EventoPagoAprobado.class);
        verify(publicador).publicarPagoAprobado(evento.capture());
        assertEquals(7L, evento.getValue().reservaId());
        assertEquals("REF-9", evento.getValue().referenciaPasarela());
        assertEquals("ana@test.com", evento.getValue().reservaPagada().email());
        assertEquals(5L, evento.getValue().reservaPagada().hotelId());
    }

    @Test
    void pagoGuardaElUsuarioActualYLaClaveNormalizada() {
        reservaPendiente(7, "100", "ARS");
        pasarelaAprueba();
        when(repositorio.buscarPorClaveIdempotencia(1L, CLAVE_VALIDA)).thenReturn(Optional.empty());

        servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100"), "ARS", "  " + CLAVE_VALIDA + "  "));

        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);
        verify(repositorio, atLeastOnce()).guardar(pago.capture());
        assertEquals(1L, pago.getValue().getUsuarioId());
        assertEquals(CLAVE_VALIDA, pago.getValue().getClaveIdempotencia());
        assertEquals(EstadoPago.APROBADO, pago.getValue().getEstado());
    }

    @Test
    void monedaSeComparaSinDistinguirMayusculas() {
        reservaPendiente(7, "100", "ARS");
        pasarelaAprueba();

        PagoResponse r = servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100.00"), "ars"));

        assertEquals("APROBADO", r.estado());
    }

    @Test
    void pasarelaQueRechazaDejaElPagoRechazadoYNoConfirma() {
        reservaPendiente(7, "100", "ARS");
        when(pasarela.cobrar(any(), anyString(), anyString())).thenReturn(new ResultadoCobro(false, null, "Fondos insuficientes"));

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100"), "ARS")));

        assertEquals(CodigoErrorPago.PAGO_RECHAZADO, ex.getCodigo());
        assertEquals("Fondos insuficientes", ex.getMessage());
        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);
        verify(repositorio, times(2)).guardar(pago.capture());
        assertEquals(EstadoPago.RECHAZADO, pago.getValue().getEstado());
        verify(reservas, never()).confirmarReserva(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void rechazoSinMotivoUsaMensajePorDefecto() {
        reservaPendiente(7, "100", "ARS");
        when(pasarela.cobrar(any(), anyString(), anyString())).thenReturn(new ResultadoCobro(false, null, null));

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100"), "ARS")));

        assertEquals(CodigoErrorPago.PAGO_RECHAZADO, ex.getCodigo());
        assertEquals("La pasarela rechazó el pago", ex.getMessage());
    }

    @Test
    void pasarelaCaidaPropagaElErrorYNoConfirmaNiPublica() {
        reservaPendiente(7, "100", "ARS");
        when(pasarela.cobrar(any(), anyString(), anyString())).thenThrow(
                new PagoException(CodigoErrorPago.ERROR_COMUNICACION_PASARELA, "Pasarela caída"));

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100"), "ARS")));

        assertEquals(CodigoErrorPago.ERROR_COMUNICACION_PASARELA, ex.getCodigo());
        verify(repositorio, times(1)).guardar(any(Pago.class));
        verify(reservas, never()).confirmarReserva(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void solicitudNulaSeRechaza() {
        PagoException ex = assertThrows(PagoException.class, () -> servicio.procesarPago(null));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(pasarela);
    }

    @Test
    void reservaIdNuloSeRechaza() {
        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(null, BigDecimal.TEN, "ARS")));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
    }

    @Test
    void montoNuloSeRechaza() {
        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, null, "ARS")));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void montoNoPositivoSeRechazaSinTocarLaPasarela(String monto) {
        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal(monto), "ARS")));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(pasarela);
        verify(repositorio, never()).guardar(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void monedaVaciaSeRechaza(String moneda) {
        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, moneda)));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
    }

    @Test
    void usuarioQueNoEsHuespedNoPuedePagar() {
        doThrow(new SeguridadException(403, "Solo huéspedes pueden reservar o pagar")).when(identidad).exigirHuesped();

        SeguridadException ex = assertThrows(SeguridadException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, "ARS")));

        assertEquals(403, ex.getEstado());
        verifyNoInteractions(pasarela, repositorio, reservas);
    }

    @Test
    void reservaNoPendienteDevuelve409() {
        when(reservas.consultarParaPago(7L)).thenReturn(reserva(7, "CONFIRMADA", "100", "ARS"));

        SeguridadException ex = assertThrows(SeguridadException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100"), "ARS")));

        assertEquals(409, ex.getEstado());
        assertEquals("La reserva no está pendiente de pago", ex.getMessage());
        verifyNoInteractions(pasarela);
    }

    @Test
    void reservaYaPagadaSeRechazaSinCobrarDeNuevo() {
        reservaPendiente(7, "100", "ARS");
        when(repositorio.existeAprobadoParaReserva(7L)).thenReturn(true);

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100"), "ARS")));

        assertEquals(CodigoErrorPago.RESERVA_YA_PAGADA, ex.getCodigo());
        assertTrue(ex.getMessage().contains("7"));
        verifyNoInteractions(pasarela);
    }

    @Test
    void montoDistintoAlPrecioDeLaReservaSeRechaza() {
        reservaPendiente(7, "100", "ARS");

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("99"), "ARS")));

        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(pasarela);
        verify(repositorio, never()).guardar(any());
    }

    @Test
    void montoMayorAlPrecioDeLaReservaTambienSeRechaza() {
        reservaPendiente(7, "100", "ARS");

        assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("101"), "ARS")));

        verifyNoInteractions(pasarela);
    }

    @Test
    void monedaDistintaALaDeLaReservaSeRechaza() {
        reservaPendiente(7, "100", "ARS");

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, new BigDecimal("100"), "USD")));

        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(pasarela);
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta", "con espacios internos 12345", "caracter-invalido-ñ-123456"})
    void claveDeIdempotenciaInsegura(String clave) {
        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, "ARS", clave)));

        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(pasarela);
    }

    @Test
    void claveDeMasDe80CaracteresSeRechaza() {
        assertThrows(PagoException.class,
                () -> servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, "ARS", "a".repeat(81))));
        verifyNoInteractions(pasarela);
    }

    @Test
    void claveEnBlancoSeIgnoraYNoConsultaRepetidos() {
        reservaPendiente(7, "10", "ARS");
        pasarelaAprueba();

        PagoResponse r = servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, "ARS", "   "));

        assertEquals("APROBADO", r.estado());
        verify(repositorio, never()).buscarPorClaveIdempotencia(any(), any());
    }

    @Test
    void claveLimiteDe16Y80CaracteresEsValida() {
        reservaPendiente(7, "10", "ARS");
        pasarelaAprueba();

        assertEquals("APROBADO",
                servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, "ARS", "a".repeat(16))).estado());
        assertEquals("APROBADO",
                servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, "ARS", "b".repeat(80))).estado());
    }

    @Test
    void pagoRepetidoConMismaClaveDevuelveElExistenteSinCobrar() throws Exception {
        Pago existente = conId(new Pago(1L, List.of(7L), BigDecimal.TEN, "ARS", CLAVE_VALIDA), 55L);
        existente.aprobar("REF-ANTIGUA");
        when(repositorio.buscarPorClaveIdempotencia(1L, CLAVE_VALIDA)).thenReturn(Optional.of(existente));
        when(repositorio.buscarPorId(55L)).thenReturn(Optional.of(existente));

        PagoResponse r = servicio.procesarPago(new PagoRequest(7L, BigDecimal.TEN, "ARS", CLAVE_VALIDA));

        assertEquals(55L, r.id());
        assertEquals("REF-ANTIGUA", r.referenciaPasarela());
        verifyNoInteractions(pasarela, publicador);
        verify(repositorio, never()).guardar(any());
        verify(repositorio, never()).bloquearReservas(anyList());
    }

    // ---------------------------------------------------------------- procesarLote

    @Test
    void loteAprobadoConfirmaCadaReservaYPublicaUnEventoPorReserva() {
        reservaPendiente(7, "100", "ARS");
        reservaPendiente(8, "50", "ARS");
        pasarelaAprueba();

        PagoResponse r = servicio.procesarLote(new PagoLoteRequest(List.of(8L, 7L), new BigDecimal("150"), "ARS"));

        assertEquals("APROBADO", r.estado());
        assertEquals(List.of(8L, 7L), r.reservaIds());
        assertEquals(8L, r.reservaId());
        assertEquals(new BigDecimal("150"), r.monto());
        verify(repositorio).bloquearReservas(List.of(8L, 7L));
        verify(pasarela).cobrar(eq(new BigDecimal("150")), eq("ARS"), startsWith("checkout-"));
        verify(reservas).confirmarReserva(7L);
        verify(reservas).confirmarReserva(8L);
        ArgumentCaptor<EventoPagoAprobado> eventos = ArgumentCaptor.forClass(EventoPagoAprobado.class);
        verify(publicador, times(2)).publicarPagoAprobado(eventos.capture());
        assertEquals(List.of(8L, 7L), eventos.getAllValues().stream().map(EventoPagoAprobado::reservaId).toList());
    }

    @Test
    void loteRechazadoPorLaPasarelaNoConfirmaNada() {
        reservaPendiente(7, "100", "ARS");
        reservaPendiente(8, "50", "ARS");
        when(pasarela.cobrar(any(), anyString(), anyString())).thenReturn(new ResultadoCobro(false, null, "Tarjeta vencida"));

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L, 8L), new BigDecimal("150"), "ARS")));

        assertEquals(CodigoErrorPago.PAGO_RECHAZADO, ex.getCodigo());
        assertEquals("Tarjeta vencida", ex.getMessage());
        verify(reservas, never()).confirmarReserva(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void loteConExactamenteVeinteReservasEsAceptado() {
        List<Long> ids = LongStream.rangeClosed(1, 20).boxed().toList();
        ids.forEach(id -> reservaPendiente(id, "1", "ARS"));
        pasarelaAprueba();

        PagoResponse r = servicio.procesarLote(new PagoLoteRequest(ids, new BigDecimal("20"), "ARS"));

        assertEquals("APROBADO", r.estado());
        verify(publicador, times(20)).publicarPagoAprobado(any());
    }

    @Test
    void loteDeMasDeVeinteReservasDevuelve400() {
        List<Long> ids = LongStream.rangeClosed(1, 21).boxed().toList();

        SeguridadException ex = assertThrows(SeguridadException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(ids, BigDecimal.TEN, "ARS")));

        assertEquals(400, ex.getEstado());
        verifyNoInteractions(pasarela, reservas);
    }

    @Test
    void loteSolicitudNulaSeRechaza() {
        PagoException ex = assertThrows(PagoException.class, () -> servicio.procesarLote(null));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
    }

    @Test
    void loteSinListaDeReservasSeRechaza() {
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(null, BigDecimal.TEN, "ARS"))).getCodigo());
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(), BigDecimal.TEN, "ARS"))).getCodigo());
    }

    @Test
    void loteConIdNuloONoPositivoSeRechaza() {
        assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(Arrays.asList(7L, null), BigDecimal.TEN, "ARS")));
        assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L, 0L), BigDecimal.TEN, "ARS")));
        assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(-3L), BigDecimal.TEN, "ARS")));
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteConReservasDuplicadasSeRechaza() {
        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L, 7L), new BigDecimal("20"), "ARS")));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteConMontoInvalidoSeRechaza() {
        assertThrows(PagoException.class, () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L), null, "ARS")));
        assertThrows(PagoException.class, () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L), BigDecimal.ZERO, "ARS")));
        assertThrows(PagoException.class, () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L), new BigDecimal("-5"), "ARS")));
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteConMonedaInvalidaSeRechaza() {
        assertThrows(PagoException.class, () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L), BigDecimal.TEN, null)));
        assertThrows(PagoException.class, () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L), BigDecimal.TEN, " ")));
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteConClaveInsegura() {
        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L), BigDecimal.TEN, "ARS", "corta")));
        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
    }

    @Test
    void loteNoHuespedSeRechaza() {
        doThrow(new SeguridadException(403, "Solo huéspedes pueden reservar o pagar")).when(identidad).exigirHuesped();
        SeguridadException ex = assertThrows(SeguridadException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L), BigDecimal.TEN, "ARS")));
        assertEquals(403, ex.getEstado());
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteRepetidoDevuelveElPagoExistente() throws Exception {
        Pago existente = conId(new Pago(1L, List.of(7L, 8L), new BigDecimal("30"), "ARS", CLAVE_VALIDA), 70L);
        when(repositorio.buscarPorClaveIdempotencia(1L, CLAVE_VALIDA)).thenReturn(Optional.of(existente));
        when(repositorio.buscarPorId(70L)).thenReturn(Optional.of(existente));

        PagoResponse r = servicio.procesarLote(new PagoLoteRequest(List.of(7L, 8L), new BigDecimal("30"), "ARS", CLAVE_VALIDA));

        assertEquals(70L, r.id());
        assertEquals(List.of(7L, 8L), r.reservaIds());
        verifyNoInteractions(pasarela, publicador);
        verify(repositorio, never()).bloquearReservas(anyList());
    }

    @Test
    void loteConReservaNoPendienteDevuelve409SinCobrar() {
        reservaPendiente(7, "10", "ARS");
        when(reservas.consultarParaPago(8L)).thenReturn(reserva(8, "CANCELADA", "10", "ARS"));

        SeguridadException ex = assertThrows(SeguridadException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L, 8L), new BigDecimal("20"), "ARS")));

        assertEquals(409, ex.getEstado());
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteConReservaYaPagadaSeRechaza() {
        reservaPendiente(7, "10", "ARS");
        reservaPendiente(8, "10", "ARS");
        when(repositorio.existeAprobadoParaReserva(8L)).thenReturn(true);

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L, 8L), new BigDecimal("20"), "ARS")));

        assertEquals(CodigoErrorPago.RESERVA_YA_PAGADA, ex.getCodigo());
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteConMonedasMezcladasSeRechaza() {
        reservaPendiente(7, "10", "ARS");
        reservaPendiente(8, "10", "USD");

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L, 8L), new BigDecimal("20"), "ARS")));

        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        assertEquals("Todas las reservas deben usar la misma moneda", ex.getMessage());
        verifyNoInteractions(pasarela);
    }

    @Test
    void loteConTotalQueNoCoincideSeRechaza() {
        reservaPendiente(7, "10", "ARS");
        reservaPendiente(8, "10", "ARS");

        PagoException ex = assertThrows(PagoException.class,
                () -> servicio.procesarLote(new PagoLoteRequest(List.of(7L, 8L), new BigDecimal("19"), "ARS")));

        assertEquals(CodigoErrorPago.SOLICITUD_INVALIDA, ex.getCodigo());
        assertEquals("El total del checkout no coincide con las reservas", ex.getMessage());
        verifyNoInteractions(pasarela);
        verify(repositorio, never()).guardar(any());
    }

    // ---------------------------------------------------------------- consultarPago

    @Test
    void consultarPagoExistenteDevuelveSuRespuesta() throws Exception {
        Pago pago = conId(new Pago(1L, List.of(7L, 8L), new BigDecimal("30"), "ARS", null), 9L);
        pago.aprobar("REF-1");
        when(repositorio.buscarPorId(9L)).thenReturn(Optional.of(pago));

        PagoResponse r = servicio.consultarPago(9L);

        assertEquals(9L, r.id());
        assertEquals("APROBADO", r.estado());
        assertEquals("REF-1", r.referenciaPasarela());
        InOrder orden = inOrder(reservas);
        orden.verify(reservas).consultarReserva(7L);
        orden.verify(reservas).consultarReserva(8L);
    }

    @Test
    void consultarPagoConIdNuloONoPositivoDevuelve404() {
        for (Long id : new Long[]{null, 0L, -4L}) {
            SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.consultarPago(id));
            assertEquals(404, ex.getEstado());
            assertEquals("Pago no disponible", ex.getMessage());
        }
        verifyNoInteractions(repositorio);
    }

    @Test
    void consultarPagoInexistenteDevuelve404() {
        when(repositorio.buscarPorId(9L)).thenReturn(Optional.empty());

        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.consultarPago(9L));

        assertEquals(404, ex.getEstado());
    }

    @Test
    void consultarPagoSinSesionPropagaElErrorDeIdentidad() {
        when(identidad.usuarioId()).thenThrow(new SeguridadException(401, "Iniciá sesión para continuar"));

        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.consultarPago(9L));

        assertEquals(401, ex.getEstado());
        verifyNoInteractions(repositorio);
    }

    @Test
    void consultarPagoDeReservaAjenaSeOcultaComo404() {
        Pago pago = new Pago(2L, List.of(7L), BigDecimal.TEN, "ARS", null);
        when(repositorio.buscarPorId(9L)).thenReturn(Optional.of(pago));
        when(reservas.consultarReserva(7L)).thenThrow(new SeguridadException(404, "Reserva no disponible"));

        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.consultarPago(9L));

        assertEquals(404, ex.getEstado());
        assertEquals("Pago no disponible", ex.getMessage());
    }

    @Test
    void consultarPagoConOtroErrorDeSeguridadLoRelanzaIntacto() {
        Pago pago = new Pago(1L, List.of(7L), BigDecimal.TEN, "ARS", null);
        when(repositorio.buscarPorId(9L)).thenReturn(Optional.of(pago));
        when(reservas.consultarReserva(7L)).thenThrow(new SeguridadException(403, "Prohibido"));

        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.consultarPago(9L));

        assertEquals(403, ex.getEstado());
        assertEquals("Prohibido", ex.getMessage());
    }

    // ---------------------------------------------------------------- listados

    @Test
    void listarPorReservasConsultaCadaReservaYDevuelveLosPagos() throws Exception {
        Pago p1 = conId(new Pago(1L, List.of(7L), BigDecimal.TEN, "ARS", null), 1L);
        Pago p2 = conId(new Pago(1L, List.of(8L), BigDecimal.ONE, "ARS", null), 2L);
        when(repositorio.listarPorReservas(List.of(7L, 8L))).thenReturn(List.of(p1, p2));
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(p1));
        when(repositorio.buscarPorId(2L)).thenReturn(Optional.of(p2));

        List<PagoResponse> r = servicio.listarPorReservas(List.of(7L, 8L));

        assertEquals(List.of(1L, 2L), r.stream().map(PagoResponse::id).toList());
        verify(reservas, atLeastOnce()).consultarReserva(7L);
        verify(reservas, atLeastOnce()).consultarReserva(8L);
    }

    @Test
    void listarPorReservasNuloDevuelve400() {
        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.listarPorReservas(null));
        assertEquals(400, ex.getEstado());
    }

    @Test
    void listarPorReservasAcepta100PeroNoMas() {
        List<Long> cien = LongStream.rangeClosed(1, 100).boxed().toList();
        when(repositorio.listarPorReservas(cien)).thenReturn(List.of());
        assertTrue(servicio.listarPorReservas(cien).isEmpty());

        List<Long> ciento1 = new ArrayList<>(cien);
        ciento1.add(101L);
        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.listarPorReservas(ciento1));
        assertEquals(400, ex.getEstado());
    }

    @Test
    void listarPorReservasDeReservaAjenaPropagaElError() {
        when(reservas.consultarReserva(7L)).thenThrow(new SeguridadException(404, "Reserva no disponible"));

        assertThrows(SeguridadException.class, () -> servicio.listarPorReservas(List.of(7L)));

        verify(repositorio, never()).listarPorReservas(anyList());
    }

    @Test
    void listarMiosPideLaPaginaDelUsuarioActual() throws Exception {
        Pago p = conId(new Pago(1L, List.of(7L), BigDecimal.TEN, "ARS", null), 3L);
        when(repositorio.listarPorUsuario(1L, 10, 25)).thenReturn(List.of(p));
        when(repositorio.buscarPorId(3L)).thenReturn(Optional.of(p));

        List<PagoResponse> r = servicio.listarMios(10, 25);

        assertEquals(1, r.size());
        assertEquals(3L, r.get(0).id());
        verify(repositorio).listarPorUsuario(1L, 10, 25);
    }

    @Test
    void listarMiosAceptaLosLimitesDePaginacion() {
        when(repositorio.listarPorUsuario(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        assertTrue(servicio.listarMios(0, 1).isEmpty());
        assertTrue(servicio.listarMios(10000, 100).isEmpty());
    }

    @Test
    void listarMiosConPaginacionInvalidaDevuelve400() {
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.listarMios(-1, 10)).getEstado());
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.listarMios(10001, 10)).getEstado());
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.listarMios(0, 0)).getEstado());
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.listarMios(0, 101)).getEstado());
        verify(repositorio, never()).listarPorUsuario(anyLong(), anyInt(), anyInt());
    }
}
