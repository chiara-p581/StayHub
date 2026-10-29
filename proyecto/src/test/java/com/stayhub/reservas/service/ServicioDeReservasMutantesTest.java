package com.stayhub.reservas.service;

import static com.stayhub.seguridad.IdentidadFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stayhub.canalesexternos.contrato.interno.SolicitudReserva;
import com.stayhub.canalesexternos.dto.HuespedDTO;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.dto.ReservaRequest;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Casos límite de ServicioDeReservasImpl que verifican lógica puntual (tests de mutación). */
class ServicioDeReservasMutantesTest {
    ServicioDeReservasImpl servicio;
    ReservaRepository repo;
    PagoRepository pagos;
    GestionDeDisponibilidadPort cupo;
    ServicioDeInventarioYTarifas tarifas;
    LocalDate desde = LocalDate.now().plusDays(3), hasta = desde.plusDays(2);

    @SuppressWarnings("unchecked")
    @BeforeEach void preparar() throws Exception {
        servicio = new ServicioDeReservasImpl();
        repo = mock(ReservaRepository.class);
        pagos = mock(PagoRepository.class);
        cupo = mock(GestionDeDisponibilidadPort.class);
        tarifas = mock(ServicioDeInventarioYTarifas.class);
        Instance<GestionDeDisponibilidadPort> instancia = mock(Instance.class);
        when(instancia.isResolvable()).thenReturn(true);
        when(instancia.get()).thenReturn(cupo);
        campo(servicio, "repositorio", repo);
        campo(servicio, "pagos", pagos);
        campo(servicio, "tarifas", tarifas);
        campo(servicio, "disponibilidad", instancia);
        campo(servicio, "identidad", usuario(1L, "HUESPED"));
    }

    // ---------------- reemplazarHoldYConfirmar (rama sin hold) ----------------

    @Test void modificarDesdeCanalSobreReservaSinHoldActualizaLosDatosDeLaEntidad() {
        Reserva r = new Reserva("BOOKING", "REF-1", 1L, "DOBLE", 2, desde, hasta,
                new Huesped("Ana", "Paz", "ana@mail.com", "123"), new BigDecimal("1000.50"), "ARS");
        r.rechazar();
        when(repo.buscarPorCanalYReferencia("BOOKING", "REF-1")).thenReturn(Optional.of(r));
        LocalDate nuevoIn = desde.plusDays(10), nuevoOut = nuevoIn.plusDays(4);
        when(cupo.crearHold(1L, "SUITE", 3, nuevoIn, nuevoOut)).thenReturn("hold-nuevo");

        var res = servicio.modificarDesdeCanal(new SolicitudReserva("REF-1", "BOOKING", 1L, nuevoIn, nuevoOut, "SUITE", 3,
                new HuespedDTO("Ana", "Paz", "ana@mail.com", "123"), new BigDecimal("2500.00"), "ARS"));

        assertEquals("CONFIRMADA", res.estado());
        assertEquals(nuevoIn, r.getCheckIn());
        assertEquals(nuevoOut, r.getCheckOut());
        assertEquals("SUITE", r.getTipoHabitacion());
        assertEquals(3, r.getCantidadHabitaciones());
        assertEquals(new BigDecimal("2500.00"), r.getPrecioTotal());
        assertEquals("hold-nuevo", r.getHoldId());
        InOrder orden = inOrder(cupo, repo);
        orden.verify(cupo).crearHold(1L, "SUITE", 3, nuevoIn, nuevoOut);
        orden.verify(cupo).confirmarHold("hold-nuevo");
        orden.verify(repo).guardar(r);
        verify(cupo, never()).reemplazarHold(any(), any(), any(), anyInt(), any(), any());
    }

    // ---------------- validarModificacion: límite del precio ----------------

    Reserva pendiente() throws Exception {
        Reserva r = new Reserva("DIRECTA", null, 1L, "DOBLE", 1, desde, hasta,
                new Huesped("Real", "Cuenta", "real@mail.com", "123"), new BigDecimal("100"), "ARS");
        r.asignarPropietario(1L);
        r.iniciarHold("hold-1");
        var f = Reserva.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, 7L);
        when(repo.buscarPorId(7L)).thenReturn(Optional.of(r));
        when(repo.buscarPorIdBloqueando(7L)).thenReturn(Optional.of(r));
        return r;
    }

    ReservaRequest cambio(BigDecimal precio) {
        return new ReservaRequest(1L, "DOBLE", 1, desde, hasta, null, null, null, null, precio, "ARS");
    }

    void tarifa(String importe) {
        when(tarifas.consultarTarifas(1L, desde, hasta))
                .thenReturn(List.of(new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal(importe), "ARS")));
        when(cupo.reemplazarHold("hold-1", 1L, "DOBLE", 1, desde, hasta)).thenReturn("hold-2");
    }

    @Test void modificarReservaConPrecioSolicitadoCeroEsValida() throws Exception {
        Reserva r = pendiente();
        tarifa("100.00");
        var res = servicio.modificarReserva(7L, cambio(BigDecimal.ZERO));
        assertEquals(new BigDecimal("100.00"), res.precioTotal());
        assertEquals("hold-2", r.getHoldId());
        verify(repo).guardar(r);
    }

    @Test void modificarReservaConPrecioSolicitadoNegativoEsSolicitudInvalida() throws Exception {
        Reserva r = pendiente();
        tarifa("100.00");
        var error = assertThrows(ReservaException.class, () -> servicio.modificarReserva(7L, cambio(new BigDecimal("-0.01"))));
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
        assertEquals("hold-1", r.getHoldId());
        verify(repo, never()).guardar(any());
        verifyNoInteractions(cupo);
    }

    // ---------------- precio(): límites del total ----------------

    @Test void totalExactamenteCeroEsAceptado() throws Exception {
        pendiente();
        tarifa("0");
        var res = servicio.modificarReserva(7L, cambio(BigDecimal.ONE));
        assertEquals(0, BigDecimal.ZERO.compareTo(res.precioTotal()));
    }

    @Test void totalConEscalaDosYPrecisionDoceEsAceptado() throws Exception {
        pendiente();
        tarifa("9999999999.99");
        var res = servicio.modificarReserva(7L, cambio(BigDecimal.ONE));
        assertEquals(new BigDecimal("9999999999.99"), res.precioTotal());
    }

    @Test void totalConEscalaTresSeRechazaConEstado400() throws Exception {
        Reserva r = pendiente();
        tarifa("1.001");
        var error = assertThrows(SeguridadException.class, () -> servicio.modificarReserva(7L, cambio(BigDecimal.ONE)));
        assertEquals(400, error.getEstado());
        assertEquals("El total de la reserva supera los límites permitidos", error.getMessage());
        assertEquals("hold-1", r.getHoldId());
        verify(repo, never()).guardar(any());
    }

    @Test void totalConPrecisionTreceSeRechazaConEstado400() throws Exception {
        pendiente();
        tarifa("99999999999.99");
        var error = assertThrows(SeguridadException.class, () -> servicio.modificarReserva(7L, cambio(BigDecimal.ONE)));
        assertEquals(400, error.getEstado());
        verify(repo, never()).guardar(any());
    }

    @Test void totalNegativoSeRechazaConEstado400() throws Exception {
        pendiente();
        tarifa("-1.00");
        var error = assertThrows(SeguridadException.class, () -> servicio.modificarReserva(7L, cambio(BigDecimal.ONE)));
        assertEquals(400, error.getEstado());
        verify(repo, never()).guardar(any());
    }

    @Test void reservaRechazadaNoSePuedeModificarPorElCaminoDirecto() throws Exception {
        Reserva r = pendiente();
        r.rechazar();
        var error = assertThrows(ReservaException.class, () -> servicio.modificarReserva(7L, cambio(BigDecimal.ONE)));
        assertEquals(CodigoErrorReserva.TRANSICION_DE_ESTADO_INVALIDA, error.getCodigo());
        assertEquals(EstadoReserva.RECHAZADA, r.getEstado());
    }
}
