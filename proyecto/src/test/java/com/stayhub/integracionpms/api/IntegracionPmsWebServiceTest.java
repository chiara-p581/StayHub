package com.stayhub.integracionpms.api;

import com.stayhub.integracionpms.dto.DisponibilidadPmsDTO;
import com.stayhub.integracionpms.dto.ReservaPmsDTO;
import com.stayhub.integracionpms.exception.IntegracionPmsFault;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.service.ServicioDeReservas;

import jakarta.ejb.EJBException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Prueba la fachada SOAP sin levantar WildFly: que delegue en los
 * componentes internos, que traduzca los tipos al contrato XML y que
 * convierta los errores de negocio en el SOAP Fault declarado en el WSDL.
 */
class IntegracionPmsWebServiceTest {

    private IntegracionPmsWebService servicio;
    private ServicioDeReservas reservas;
    private ServicioDeInventarioYTarifas inventario;

    @BeforeEach
    void preparar() throws Exception {
        servicio = new IntegracionPmsWebService();
        reservas = mock(ServicioDeReservas.class);
        inventario = mock(ServicioDeInventarioYTarifas.class);
        setPrivateField(servicio, "reservas", reservas);
        setPrivateField(servicio, "inventario", inventario);
    }

    @Test
    void consultarReservaTraduceAlContratoSoap() throws Exception {
        when(reservas.consultarReserva(7L)).thenReturn(reserva(7L, "CONFIRMADA", LocalDate.of(2026, 11, 9)));

        ReservaPmsDTO dto = servicio.consultarReserva(7L);

        assertEquals(7L, dto.getId());
        assertEquals("2026-11-09", dto.getCheckIn());
        assertEquals("CONFIRMADA", dto.getEstado());
        assertEquals("Perez", dto.getHuespedApellido());
    }

    @Test
    void reservaInexistenteSeDevuelveComoSoapFault() {
        when(reservas.consultarReserva(99L)).thenThrow(
                new ReservaException(CodigoErrorReserva.RESERVA_NO_ENCONTRADA, "No existe la reserva 99"));

        IntegracionPmsFault fault = assertThrows(IntegracionPmsFault.class, () -> servicio.consultarReserva(99L));

        assertEquals("RESERVA_NO_ENCONTRADA", fault.getFaultInfo().getCodigo());
        assertEquals("No existe la reserva 99", fault.getFaultInfo().getMensaje());
    }

    @Test
    void errorDeNegocioEnvueltoPorElContenedorTambienSeTraduce() {
        when(reservas.consultarReserva(5L)).thenThrow(new EJBException(
                new ReservaException(CodigoErrorReserva.DEPENDENCIA_NO_DISPONIBLE, "Inventario caído")));

        IntegracionPmsFault fault = assertThrows(IntegracionPmsFault.class, () -> servicio.consultarReserva(5L));

        assertEquals("DEPENDENCIA_NO_DISPONIBLE", fault.getFaultInfo().getCodigo());
    }

    @Test
    void listarLlegadasFiltraConfirmadasDelDia() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 9);
        when(reservas.listarPorHotel(1L)).thenReturn(List.of(
                reserva(1L, "CONFIRMADA", hoy),
                reserva(2L, "PENDIENTE", hoy),
                reserva(3L, "CONFIRMADA", hoy.plusDays(1)),
                reserva(4L, "CANCELADA", hoy)));

        List<ReservaPmsDTO> llegadas = servicio.listarLlegadas(1L, "2026-11-09");

        assertEquals(1, llegadas.size());
        assertEquals(1L, llegadas.get(0).getId());
    }

    @Test
    void fechaMalFormadaEsSolicitudInvalida() {
        IntegracionPmsFault fault = assertThrows(IntegracionPmsFault.class,
                () -> servicio.listarLlegadas(1L, "09/11/2026"));

        assertEquals("SOLICITUD_INVALIDA", fault.getFaultInfo().getCodigo());
        verifyNoInteractions(reservas);
    }

    @Test
    void cancelarReservaActuaComoAdminDelHotel() throws Exception {
        when(reservas.cancelarReserva(7L, IntegracionPmsWebService.ACTOR_PMS, true))
                .thenReturn(reserva(7L, "CANCELADA", LocalDate.of(2026, 11, 9)));

        ReservaPmsDTO dto = servicio.cancelarReserva(7L);

        assertEquals("CANCELADA", dto.getEstado());
        verify(reservas).cancelarReserva(7L, IntegracionPmsWebService.ACTOR_PMS, true);
    }

    @Test
    void consultarDisponibilidadDelegaEnInventario() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 9);
        LocalDate hasta = LocalDate.of(2026, 11, 12);
        when(inventario.consultarDisponibilidad(1L, desde, hasta)).thenReturn(List.of(
                new DisponibilidadDTO(1L, "DOBLE", desde, hasta, 4)));

        List<DisponibilidadPmsDTO> disp = servicio.consultarDisponibilidad(1L, "2026-11-09", "2026-11-12");

        assertEquals(1, disp.size());
        assertEquals("DOBLE", disp.get(0).getTipoHabitacion());
        assertEquals(4, disp.get(0).getUnidadesDisponibles());
    }

    private static ReservaResponse reserva(Long id, String estado, LocalDate checkIn) {
        return new ReservaResponse(id, "DIRECTA", null, 1L, "DOBLE", 1, checkIn, checkIn.plusDays(2),
                "Ana", "Perez", "ana@mail.com", new BigDecimal("150000.00"), "ARS", estado,
                LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    private static void setPrivateField(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
