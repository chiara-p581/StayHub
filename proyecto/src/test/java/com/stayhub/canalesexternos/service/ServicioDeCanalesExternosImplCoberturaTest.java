package com.stayhub.canalesexternos.service;

import com.stayhub.canalesexternos.config.ConfiguracionCanales;
import com.stayhub.canalesexternos.contrato.interno.ResultadoOperacionReserva;
import com.stayhub.canalesexternos.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.canalesexternos.contrato.interno.ServicioDeReservasPort;
import com.stayhub.canalesexternos.contrato.interno.SolicitudReserva;
import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.dto.DisponibilidadDTO;
import com.stayhub.canalesexternos.dto.HuespedDTO;
import com.stayhub.canalesexternos.dto.ReservaExternaDTO;
import com.stayhub.canalesexternos.dto.ResultadoReservaDTO;
import com.stayhub.canalesexternos.dto.ResultadoSincronizacionDTO;
import com.stayhub.canalesexternos.exception.CanalExternoException;
import com.stayhub.canalesexternos.exception.CodigoErrorCanal;
import com.stayhub.canalesexternos.messaging.PublicadorSincronizacion;
import com.stayhub.seguridad.SeguridadException;
import jakarta.enterprise.inject.Instance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Cobertura ampliada de ServicioDeCanalesExternosImpl: validaciones, delegación y dependencias ausentes. */
class ServicioDeCanalesExternosImplCoberturaTest {

    private final LocalDate entrada = LocalDate.now().plusDays(10);
    private final LocalDate salida = entrada.plusDays(2);

    private ServicioDeCanalesExternosImpl servicio;
    private ServicioDeReservasPort puertoReservas;
    private ServicioDeInventarioYTarifasPort puertoInventario;
    private PublicadorSincronizacion publicador;
    private ConfiguracionCanales configuracion;
    private Instance<ServicioDeReservasPort> instanciaReservas;
    private Instance<ServicioDeInventarioYTarifasPort> instanciaInventario;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        servicio = new ServicioDeCanalesExternosImpl();
        puertoReservas = mock(ServicioDeReservasPort.class);
        puertoInventario = mock(ServicioDeInventarioYTarifasPort.class);
        publicador = mock(PublicadorSincronizacion.class);
        configuracion = mock(ConfiguracionCanales.class);
        instanciaReservas = mock(Instance.class);
        instanciaInventario = mock(Instance.class);
        when(instanciaReservas.isResolvable()).thenReturn(true);
        when(instanciaReservas.get()).thenReturn(puertoReservas);
        when(instanciaInventario.isResolvable()).thenReturn(true);
        when(instanciaInventario.get()).thenReturn(puertoInventario);
        servicio.reservas = instanciaReservas;
        servicio.inventarioYTarifas = instanciaInventario;
        servicio.publicadorSincronizacion = publicador;
        servicio.configuracion = configuracion;
    }

    private ReservaExternaDTO reservaValida() {
        return new ReservaExternaDTO("BK-1", Canal.BOOKING, 3L, entrada, salida, "DOBLE", 2,
                new HuespedDTO("Ana", "Paz", "ana@test.com", "111"), new BigDecimal("250.50"), "USD", null);
    }

    private ReservaExternaDTO conReserva(UnaryOperator<Builder> cambio) {
        return cambio.apply(new Builder()).construir();
    }

    /** Constructor auxiliar mutable para armar variantes inválidas de la reserva. */
    private final class Builder {
        String id = "BK-1"; Canal canal = Canal.BOOKING; Long hotel = 3L; LocalDate in = entrada; LocalDate out = salida;
        String tipo = "DOBLE"; int cant = 2; HuespedDTO huesped = new HuespedDTO("Ana", "Paz", "ana@test.com", "111");
        BigDecimal precio = new BigDecimal("250.50"); String moneda = "USD";
        ReservaExternaDTO construir() {
            return new ReservaExternaDTO(id, canal, hotel, in, out, tipo, cant, huesped, precio, moneda, null);
        }
    }

    private void assertSolicitudInvalida(ReservaExternaDTO reserva) {
        CanalExternoException ex = assertThrows(CanalExternoException.class, () -> servicio.recibirReserva(reserva));
        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(puertoReservas);
    }

    // ------------------------------------------------------------ disponibilidad

    @Test
    void consultarDisponibilidadDelegaEnElPuertoDeInventario() {
        var esperado = List.of(new DisponibilidadDTO(3L, "DOBLE", entrada, salida, 4));
        when(puertoInventario.consultarDisponibilidad(3L, entrada, salida)).thenReturn(esperado);

        var resultado = servicio.consultarDisponibilidad(3L, entrada, salida);

        assertEquals(esperado, resultado);
        verify(puertoInventario).consultarDisponibilidad(3L, entrada, salida);
    }

    @Test
    void consultarDisponibilidadSinInventarioDisponibleFallaConDependencia() {
        when(instanciaInventario.isResolvable()).thenReturn(false);

        CanalExternoException ex = assertThrows(CanalExternoException.class,
                () -> servicio.consultarDisponibilidad(3L, entrada, salida));

        assertEquals(CodigoErrorCanal.DEPENDENCIA_NO_DISPONIBLE, ex.getCodigo());
        assertTrue(ex.getMessage().contains("ServicioDeInventarioYTarifas"));
        verify(instanciaInventario, never()).get();
    }

    @Test
    void consultarDisponibilidadSinHotelEsInvalida() {
        CanalExternoException ex = assertThrows(CanalExternoException.class,
                () -> servicio.consultarDisponibilidad(null, entrada, salida));
        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(puertoInventario);
    }

    @Test
    void consultarDisponibilidadConFechasNulasEsInvalida() {
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.consultarDisponibilidad(3L, null, salida)).getEstado());
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.consultarDisponibilidad(3L, entrada, null)).getEstado());
    }

    @Test
    void consultarDisponibilidadConPeriodoMayorA31NochesEsInvalida() {
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.consultarDisponibilidad(3L, entrada, entrada.plusDays(32))).getEstado());
        verifyNoInteractions(puertoInventario);
    }

    @Test
    void consultarDisponibilidadConExactamente31NochesEsValida() {
        when(puertoInventario.consultarDisponibilidad(3L, entrada, entrada.plusDays(31))).thenReturn(List.of());
        assertTrue(servicio.consultarDisponibilidad(3L, entrada, entrada.plusDays(31)).isEmpty());
    }

    // ------------------------------------------------------------ reservas

    @Test
    void recibirReservaMapeaLaSolicitudYDevuelveElResultado() {
        when(puertoReservas.crearDesdeCanal(any())).thenReturn(new ResultadoOperacionReserva("R-99", "CONFIRMADA"));

        ResultadoReservaDTO r = servicio.recibirReserva(reservaValida());

        assertEquals("R-99", r.reservaId());
        assertEquals("BK-1", r.idExterno());
        assertEquals(Canal.BOOKING, r.canal());
        assertEquals("CONFIRMADA", r.estado());
        ArgumentCaptor<SolicitudReserva> captor = ArgumentCaptor.forClass(SolicitudReserva.class);
        verify(puertoReservas).crearDesdeCanal(captor.capture());
        SolicitudReserva s = captor.getValue();
        assertEquals("BK-1", s.referenciaExterna());
        assertEquals("BOOKING", s.canal());
        assertEquals(3L, s.hotelId());
        assertEquals(entrada, s.checkIn());
        assertEquals(salida, s.checkOut());
        assertEquals("DOBLE", s.tipoHabitacion());
        assertEquals(2, s.cantidadHabitaciones());
        assertEquals("ana@test.com", s.huesped().email());
        assertEquals(new BigDecimal("250.50"), s.precioTotal());
        assertEquals("USD", s.moneda());
        verify(puertoReservas, never()).modificarDesdeCanal(any());
    }

    @Test
    void recibirReservaConPrecioCeroEsValida() {
        when(puertoReservas.crearDesdeCanal(any())).thenReturn(new ResultadoOperacionReserva("R-1", "CONFIRMADA"));
        var gratis = conReserva(b -> { b.precio = BigDecimal.ZERO; return b; });

        assertEquals("R-1", servicio.recibirReserva(gratis).reservaId());
    }

    @Test
    void modificarReservaUsaElPuertoDeModificacion() {
        when(puertoReservas.modificarDesdeCanal(any())).thenReturn(new ResultadoOperacionReserva("R-5", "MODIFICADA"));

        ResultadoReservaDTO r = servicio.modificarReserva(reservaValida());

        assertEquals("R-5", r.reservaId());
        assertEquals("MODIFICADA", r.estado());
        verify(puertoReservas).modificarDesdeCanal(any(SolicitudReserva.class));
        verify(puertoReservas, never()).crearDesdeCanal(any());
    }

    @Test
    void modificarReservaInvalidaNoLlegaAlPuerto() {
        var invalida = conReserva(b -> { b.moneda = " "; return b; });
        CanalExternoException ex = assertThrows(CanalExternoException.class, () -> servicio.modificarReserva(invalida));
        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(puertoReservas);
    }

    @Test
    void recibirReservaSinPuertoDeReservasFallaConDependencia() {
        when(instanciaReservas.isResolvable()).thenReturn(false);

        CanalExternoException ex = assertThrows(CanalExternoException.class, () -> servicio.recibirReserva(reservaValida()));

        assertEquals(CodigoErrorCanal.DEPENDENCIA_NO_DISPONIBLE, ex.getCodigo());
        assertTrue(ex.getMessage().contains("ServicioDeReservas"));
    }

    @Test
    void recibirReservaNulaEsInvalida() {
        CanalExternoException ex = assertThrows(CanalExternoException.class, () -> servicio.recibirReserva(null));
        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, ex.getCodigo());
        assertEquals("Reserva requerida", ex.getMessage());
    }

    @Test
    void recibirReservaConCanalNuloEsInvalida() {
        assertSolicitudInvalida(conReserva(b -> { b.canal = null; return b; }));
    }

    @Test
    void recibirReservaSinHotelEsInvalida() {
        assertSolicitudInvalida(conReserva(b -> { b.hotel = null; return b; }));
    }

    @Test
    void recibirReservaConCheckInPasadoEsInvalida() {
        assertSolicitudInvalida(conReserva(b -> { b.in = LocalDate.now().minusDays(1); b.out = LocalDate.now().plusDays(1); return b; }));
    }

    @Test
    void recibirReservaQueEntraHoyEsValida() {
        when(puertoReservas.crearDesdeCanal(any())).thenReturn(new ResultadoOperacionReserva("R-2", "CONFIRMADA"));
        var hoy = conReserva(b -> { b.in = LocalDate.now(); b.out = LocalDate.now().plusDays(1); return b; });

        assertEquals("R-2", servicio.recibirReserva(hoy).reservaId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void recibirReservaSinTipoDeHabitacionEsInvalida(String tipo) {
        assertSolicitudInvalida(conReserva(b -> { b.tipo = tipo; return b; }));
    }

    @Test
    void recibirReservaSinHuespedEsInvalida() {
        assertSolicitudInvalida(conReserva(b -> { b.huesped = null; return b; }));
    }

    @Test
    void recibirReservaSinPrecioEsInvalida() {
        assertSolicitudInvalida(conReserva(b -> { b.precio = null; return b; }));
    }

    @Test
    void recibirReservaConPrecioNegativoEsInvalida() {
        assertSolicitudInvalida(conReserva(b -> { b.precio = new BigDecimal("-0.01"); return b; }));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void recibirReservaSinMonedaEsInvalida(String moneda) {
        assertSolicitudInvalida(conReserva(b -> { b.moneda = moneda; return b; }));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void recibirReservaSinNombreEsInvalida(String nombre) {
        assertSolicitudInvalida(conReserva(b -> { b.huesped = new HuespedDTO(nombre, "Paz", "a@b.com", null); return b; }));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void recibirReservaSinApellidoEsInvalida(String apellido) {
        assertSolicitudInvalida(conReserva(b -> { b.huesped = new HuespedDTO("Ana", apellido, "a@b.com", null); return b; }));
    }

    @Test
    void recibirReservaSinEmailNuloEsInvalida() {
        assertSolicitudInvalida(conReserva(b -> { b.huesped = new HuespedDTO("Ana", "Paz", null, null); return b; }));
    }

    @Test
    void recibirReservaConIdExternoVacioFallaPorLimiteDeTexto() {
        SeguridadException ex = assertThrows(SeguridadException.class,
                () -> servicio.recibirReserva(conReserva(b -> { b.id = " "; return b; })));
        assertEquals(400, ex.getEstado());
        verifyNoInteractions(puertoReservas);
    }

    @Test
    void recibirReservaConIdExternoNuloOMuyLargoFalla() {
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.recibirReserva(conReserva(b -> { b.id = null; return b; }))).getEstado());
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.recibirReserva(conReserva(b -> { b.id = "X".repeat(61); return b; }))).getEstado());
    }

    @Test
    void recibirReservaConCantidadFueraDeRangoFalla() {
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.recibirReserva(conReserva(b -> { b.cant = 0; return b; }))).getEstado());
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.recibirReserva(conReserva(b -> { b.cant = 11; return b; }))).getEstado());
        verifyNoInteractions(puertoReservas);
    }

    @Test
    void recibirReservaConFechasInvertidasONulasFalla() {
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.recibirReserva(conReserva(b -> { b.in = salida; b.out = entrada; return b; }))).getEstado());
        assertEquals(400, assertThrows(SeguridadException.class,
                () -> servicio.recibirReserva(conReserva(b -> { b.in = null; return b; }))).getEstado());
    }

    // ------------------------------------------------------------ cancelar

    @Test
    void cancelarReservaDelegaConElNombreDelCanal() {
        when(puertoReservas.cancelarDesdeCanal("EXPEDIA", "EX-7")).thenReturn(new ResultadoOperacionReserva("R-7", "CANCELADA"));

        ResultadoReservaDTO r = servicio.cancelarReserva(Canal.EXPEDIA, "EX-7");

        assertEquals("R-7", r.reservaId());
        assertEquals("EX-7", r.idExterno());
        assertEquals(Canal.EXPEDIA, r.canal());
        assertEquals("CANCELADA", r.estado());
    }

    @Test
    void cancelarReservaConDatosFaltantesEsInvalida() {
        for (var par : List.of(
                new Object[]{null, "EX-7"}, new Object[]{Canal.BOOKING, null},
                new Object[]{Canal.BOOKING, ""}, new Object[]{Canal.BOOKING, "  "})) {
            CanalExternoException ex = assertThrows(CanalExternoException.class,
                    () -> servicio.cancelarReserva((Canal) par[0], (String) par[1]));
            assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, ex.getCodigo());
        }
        verifyNoInteractions(puertoReservas);
    }

    @Test
    void cancelarReservaSinPuertoFallaConDependencia() {
        when(instanciaReservas.isResolvable()).thenReturn(false);
        CanalExternoException ex = assertThrows(CanalExternoException.class,
                () -> servicio.cancelarReserva(Canal.BOOKING, "BK-1"));
        assertEquals(CodigoErrorCanal.DEPENDENCIA_NO_DISPONIBLE, ex.getCodigo());
    }

    // ------------------------------------------------------------ sincronizaciones

    @Test
    void sincronizarOtaSinCanalEsInvalidaYNoEncola() {
        CanalExternoException ex = assertThrows(CanalExternoException.class,
                () -> servicio.sincronizarOta(3L, null, entrada, salida));
        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(publicador);
    }

    @Test
    void sincronizarOtaSinHotelEsInvalida() {
        assertThrows(CanalExternoException.class, () -> servicio.sincronizarOta(null, Canal.BOOKING, entrada, salida));
        verifyNoInteractions(publicador);
    }

    @Test
    void sincronizarOtaValidaLaConfiguracionAntesDeEncolar() {
        when(publicador.publicarOta(3L, Canal.AIRBNB, entrada, salida)).thenReturn("job-1");

        ResultadoSincronizacionDTO r = servicio.sincronizarOta(3L, Canal.AIRBNB, entrada, salida);

        assertEquals("AIRBNB", r.destino());
        assertEquals("job-1", r.solicitudId());
        assertEquals(0, r.elementosProcesados());
        verify(configuracion).urlOta(Canal.AIRBNB);
    }

    @Test
    void sincronizarPmsEncolaYDevuelveDestinoPms() {
        when(publicador.publicarPms(3L, entrada, salida)).thenReturn("pms-1");

        ResultadoSincronizacionDTO r = servicio.sincronizarPms(3L, entrada, salida);

        assertEquals("PMS", r.destino());
        assertEquals("pms-1", r.solicitudId());
        assertTrue(r.exitoso());
        verify(configuracion).wsdlPms();
    }

    @Test
    void sincronizarPmsConPeriodoInvalidoNoEncola() {
        assertThrows(SeguridadException.class, () -> servicio.sincronizarPms(3L, salida, entrada));
        assertThrows(CanalExternoException.class, () -> servicio.sincronizarPms(null, entrada, salida));
        verifyNoInteractions(publicador, configuracion);
    }
}
