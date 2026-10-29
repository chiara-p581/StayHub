package com.stayhub.reservas.carrito;

import static com.stayhub.seguridad.IdentidadFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.service.ServicioDeReservas;
import com.stayhub.seguridad.SeguridadException;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.UsuarioResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CarritoDeReservaTest {
    CarritoDeReserva carrito;
    ServicioDeReservas reservas;
    ServicioDeInventarioYTarifas tarifas;
    ServicioDeUsuarios usuarios;
    LocalDate desde = LocalDate.now().plusDays(4), hasta = desde.plusDays(3);

    @BeforeEach void preparar() throws Exception {
        carrito = new CarritoDeReserva();
        reservas = mock(ServicioDeReservas.class);
        tarifas = mock(ServicioDeInventarioYTarifas.class);
        usuarios = mock(ServicioDeUsuarios.class);
        campo(carrito, "servicioDeReservas", reservas);
        campo(carrito, "inventarioYTarifas", tarifas);
        campo(carrito, "usuarios", usuarios);
        campo(carrito, "identidad", usuario(1L, "HUESPED"));
        when(usuarios.buscarPorId(1L)).thenReturn(new UsuarioResponse(1L, "real@mail.com", "Real", "Cuenta", "HUESPED"));
        when(tarifas.consultarTarifas(1L, desde, hasta)).thenReturn(List.of(
                new TarifaDTO(1L, "SUITE", desde, hasta, new BigDecimal("999"), "USD"),
                new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("100.50"), "ARS")));
    }

    void completar() {
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 2);
        carrito.seleccionarFechas(desde, hasta);
        carrito.seleccionarHuespedYPrecio("x", "y", "z@z.co", "555", BigDecimal.ONE, "XXX");
    }

    @Test void carritoNuevoEstaVacioYNoListo() {
        var r = carrito.resumen();
        assertNull(r.hotelId());
        assertNull(r.tipoHabitacion());
        assertEquals(0, r.cantidadHabitaciones());
        assertNull(r.checkIn());
        assertNull(r.precioTotal());
        assertNull(r.moneda());
        assertFalse(r.listoParaConfirmar());
    }

    @Test void seleccionarHotelYHabitacionGuardaLaEleccion() {
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 2);
        var r = carrito.resumen();
        assertEquals(1L, r.hotelId());
        assertEquals("DOBLE", r.tipoHabitacion());
        assertEquals(2, r.cantidadHabitaciones());
        assertFalse(r.listoParaConfirmar());
    }

    @Test void seleccionarHotelYHabitacionAceptaLosLimites() {
        carrito.seleccionarHotelYHabitacion(1L, "T".repeat(30), 10);
        assertEquals(10, carrito.resumen().cantidadHabitaciones());
        carrito.seleccionarHotelYHabitacion(1L, "A", 1);
        assertEquals(1, carrito.resumen().cantidadHabitaciones());
        assertEquals("A", carrito.resumen().tipoHabitacion());
    }

    @Test void seleccionarHotelYHabitacionInvalidosLanzan400YNoCambianElEstado() {
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 2);
        Object[][] casos = {{null, "DOBLE", 1}, {0L, "DOBLE", 1}, {-1L, "DOBLE", 1}, {1L, null, 1}, {1L, "  ", 1},
                {1L, "T".repeat(31), 1}, {1L, "DOBLE", 0}, {1L, "DOBLE", 11}};
        for (Object[] c : casos) {
            var error = assertThrows(SeguridadException.class,
                    () -> carrito.seleccionarHotelYHabitacion((Long) c[0], (String) c[1], (Integer) c[2]));
            assertEquals(400, error.getEstado());
        }
        var r = carrito.resumen();
        assertEquals(1L, r.hotelId());
        assertEquals("DOBLE", r.tipoHabitacion());
        assertEquals(2, r.cantidadHabitaciones());
    }

    @Test void seleccionarFechasGuardaElRango() {
        carrito.seleccionarFechas(desde, hasta);
        assertEquals(desde, carrito.resumen().checkIn());
        assertEquals(hasta, carrito.resumen().checkOut());
    }

    @Test void seleccionarFechasInvalidasLanzan400YNoCambianElEstado() {
        carrito.seleccionarFechas(desde, hasta);
        LocalDate[][] casos = {{null, hasta}, {desde, null}, {desde, desde}, {hasta, desde}, {desde, desde.plusDays(32)}};
        for (LocalDate[] c : casos) {
            assertEquals(400, assertThrows(SeguridadException.class, () -> carrito.seleccionarFechas(c[0], c[1])).getEstado());
        }
        assertEquals(desde, carrito.resumen().checkIn());
        assertEquals(hasta, carrito.resumen().checkOut());
    }

    @Test void seleccionarFechasAceptaExactamente31Noches() {
        carrito.seleccionarFechas(desde, desde.plusDays(31));
        assertEquals(desde.plusDays(31), carrito.resumen().checkOut());
    }

    @Test void huespedYPrecioSalenDeLaCuentaYDeLasTarifasNoDelCliente() {
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 2);
        carrito.seleccionarFechas(desde, hasta);
        carrito.seleccionarHuespedYPrecio("Falso", "Impostor", "falso@mail.com", "555", BigDecimal.ZERO, "USD");
        var r = carrito.resumen();
        assertEquals("Real", r.huespedNombre());
        assertEquals("Cuenta", r.huespedApellido());
        assertEquals("real@mail.com", r.huespedEmail());
        assertEquals("555", r.huespedTelefono());
        assertEquals(new BigDecimal("201.00"), r.precioTotal());
        assertEquals("ARS", r.moneda());
        assertTrue(r.listoParaConfirmar());
    }

    @Test void huespedYPrecioExigeRolHuesped() throws Exception {
        completar();
        clearInvocations(usuarios);
        campo(carrito, "identidad", usuario(9L, "ADMIN"));
        assertEquals(403, assertThrows(SeguridadException.class,
                () -> carrito.seleccionarHuespedYPrecio(null, null, null, "555", null, null)).getEstado());
        verifyNoInteractions(usuarios);
    }

    @Test void huespedYPrecioSinSesionEs401() throws Exception {
        campo(carrito, "identidad", usuario(null, null));
        assertEquals(401, assertThrows(SeguridadException.class,
                () -> carrito.seleccionarHuespedYPrecio(null, null, null, "555", null, null)).getEstado());
    }

    @Test void huespedYPrecioConTelefonoInvalidoEs400() {
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 2);
        carrito.seleccionarFechas(desde, hasta);
        for (String tel : new String[]{null, " ", "9".repeat(31)}) {
            assertEquals(400, assertThrows(SeguridadException.class,
                    () -> carrito.seleccionarHuespedYPrecio(null, null, null, tel, null, null)).getEstado());
        }
        assertNull(carrito.resumen().huespedNombre());
        assertFalse(carrito.resumen().listoParaConfirmar());
    }

    @Test void huespedYPrecioAntesDeElegirHotelYFechasEsSolicitudInvalida() {
        var error = assertThrows(ReservaException.class,
                () -> carrito.seleccionarHuespedYPrecio(null, null, null, "555", null, null));
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
        assertEquals("Elegí hotel, habitación y fechas antes de confirmar", error.getMessage());
        verifyNoInteractions(tarifas);
    }

    @Test void huespedYPrecioSinTarifaParaElTipoEsSolicitudInvalida() {
        carrito.seleccionarHotelYHabitacion(1L, "TRIPLE", 1);
        carrito.seleccionarFechas(desde, hasta);
        var error = assertThrows(ReservaException.class,
                () -> carrito.seleccionarHuespedYPrecio(null, null, null, "555", null, null));
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
        assertTrue(error.getMessage().startsWith("No hay una tarifa cargada"));
        assertNull(carrito.resumen().precioTotal());
    }

    @Test void seCalculaElPrecioSoloSiFaltaCadaDatoPrevio() {
        // cada dato previo ausente impide el cálculo: hotel, tipo, fechas
        var e1 = assertThrows(ReservaException.class, () -> {
            carrito.seleccionarFechas(desde, hasta);
            carrito.seleccionarHuespedYPrecio(null, null, null, "5", null, null);
        });
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, e1.getCodigo());
        carrito.vaciar();
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 1);
        var e2 = assertThrows(ReservaException.class, () -> carrito.seleccionarHuespedYPrecio(null, null, null, "5", null, null));
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, e2.getCodigo());
        carrito.seleccionarFechas(desde, hasta);
        carrito.seleccionarHuespedYPrecio(null, null, null, "5", null, null);
        assertTrue(carrito.resumen().listoParaConfirmar());
    }

    @Test void resumenNoEstaListoSiFaltaAlgunDatoDelHuesped() throws Exception {
        completar();
        for (String campo : new String[]{"hotelId", "tipoHabitacion", "checkIn", "checkOut", "huespedNombre", "huespedApellido",
                "huespedEmail", "huespedTelefono", "precioTotal", "moneda"}) {
            completar();
            campo(carrito, campo, null);
            assertFalse(carrito.resumen().listoParaConfirmar(), campo);
        }
        completar();
        campo(carrito, "cantidadHabitaciones", 0);
        assertFalse(carrito.resumen().listoParaConfirmar());
    }

    @Test void confirmarRecalculaElPrecioYCreaLaReservaDirecta() {
        completar();
        var esperada = new ReservaResponse(5L, "DIRECTA", null, 1L, "DOBLE", 2, desde, hasta, "Real", "Cuenta", "real@mail.com", new BigDecimal("201.00"), "ARS", "PENDIENTE", java.time.LocalDateTime.now());
        when(reservas.crearReserva(any())).thenReturn(esperada);
        var res = carrito.confirmar();
        assertSame(esperada, res);
        ArgumentCaptor<ReservaRequest> cap = ArgumentCaptor.forClass(ReservaRequest.class);
        verify(reservas).crearReserva(cap.capture());
        var q = cap.getValue();
        assertEquals(1L, q.hotelId());
        assertEquals("DOBLE", q.tipoHabitacion());
        assertEquals(2, q.cantidadHabitaciones());
        assertEquals(desde, q.checkIn());
        assertEquals(hasta, q.checkOut());
        assertEquals("Real", q.huespedNombre());
        assertEquals("Cuenta", q.huespedApellido());
        assertEquals("real@mail.com", q.huespedEmail());
        assertEquals("555", q.huespedTelefono());
        assertEquals(new BigDecimal("201.00"), q.precioTotal());
        assertEquals("ARS", q.moneda());
    }

    @Test void confirmarUsaLaTarifaVigenteAunqueCambieDespuesDeElegir() {
        completar();
        when(tarifas.consultarTarifas(1L, desde, hasta)).thenReturn(List.of(
                new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("50"), "USD")));
        carrito.confirmar();
        verify(reservas).crearReserva(argThat(q -> new BigDecimal("100").equals(q.precioTotal()) && "USD".equals(q.moneda())));
    }

    @Test void confirmarCarritoVacioFallaSinCrearReserva() {
        var error = assertThrows(ReservaException.class, () -> carrito.confirmar());
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
        verifyNoInteractions(reservas);
    }

    @Test void confirmarSinFechasFallaSinCrearReserva() {
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 1);
        assertThrows(ReservaException.class, () -> carrito.confirmar());
        verifyNoInteractions(reservas);
    }

    @Test void confirmarSinTarifaFallaSinCrearReserva() {
        carrito.seleccionarHotelYHabitacion(1L, "TRIPLE", 1);
        carrito.seleccionarFechas(desde, hasta);
        var error = assertThrows(ReservaException.class, () -> carrito.confirmar());
        assertEquals(CodigoErrorReserva.SOLICITUD_INVALIDA, error.getCodigo());
        verifyNoInteractions(reservas);
    }

    @Test void vaciarDejaElCarritoComoRecienCreado() {
        completar();
        carrito.vaciar();
        var r = carrito.resumen();
        assertNull(r.hotelId());
        assertNull(r.tipoHabitacion());
        assertEquals(0, r.cantidadHabitaciones());
        assertNull(r.checkIn());
        assertNull(r.checkOut());
        assertNull(r.huespedNombre());
        assertNull(r.huespedApellido());
        assertNull(r.huespedEmail());
        assertNull(r.huespedTelefono());
        assertNull(r.precioTotal());
        assertNull(r.moneda());
        assertFalse(r.listoParaConfirmar());
    }

    @Test void cicloDeVidaDelContenedorNoAlteraElEstadoDelCarrito() throws Exception {
        carrito.seleccionarHotelYHabitacion(1L, "DOBLE", 2);
        for (String nombre : new String[]{"iniciar", "finalizar"}) {
            var m = CarritoDeReserva.class.getDeclaredMethod(nombre);
            m.setAccessible(true);
            m.invoke(carrito);
        }
        assertEquals(1L, carrito.resumen().hotelId());
        assertEquals(2, carrito.resumen().cantidadHabitaciones());
    }
}
