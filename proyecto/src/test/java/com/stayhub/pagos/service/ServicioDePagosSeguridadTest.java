package com.stayhub.pagos.service;

import com.stayhub.seguridad.*;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.pagos.model.Pago;
import com.stayhub.pagos.dto.*;
import com.stayhub.pagos.client.pasarela.PasarelaDePagoClient;
import com.stayhub.pagos.client.pasarela.ResultadoCobro;
import com.stayhub.pagos.messaging.PublicadorEventoPago;
import com.stayhub.reservas.service.ServicioDeReservas;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.service.ServicioDeReservasImpl;
import com.stayhub.reservas.repository.ReservaRepository;
import com.stayhub.reservas.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.stayhub.seguridad.IdentidadFixture.*;

class ServicioDePagosSeguridadTest {
    ServicioDePagosImpl pagos; ReservaRepository reservasRepo; PagoRepository pagosRepo; PasarelaDePagoClient pasarela; PublicadorEventoPago publicador;
    @BeforeEach void preparar() throws Exception {
        pagos=new ServicioDePagosImpl();reservasRepo=mock(ReservaRepository.class);pagosRepo=mock(PagoRepository.class);pasarela=mock(PasarelaDePagoClient.class);publicador=mock(PublicadorEventoPago.class);
        var reservas=new ServicioDeReservasImpl();var actor=usuario(1L,"HUESPED");
        campo(reservas,"identidad",actor);campo(reservas,"repositorio",reservasRepo);
        campo(pagos,"identidad",actor);campo(pagos,"repositorio",pagosRepo);campo(pagos,"reservas",reservas);campo(pagos,"pasarela",pasarela);campo(pagos,"publicadorEventos",publicador);
    }
    void reserva(long id,long owner) {
        var r=new Reserva("DIRECTA",null,1L,"DOBLE",1,LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),new Huesped("Ana","Paz","mail@test.com","123"),BigDecimal.TEN,"ARS");
        r.asignarPropietario(owner);r.iniciarHold("hold");when(reservasRepo.buscarPorId(id)).thenReturn(Optional.of(r));when(reservasRepo.buscarPorIdBloqueando(id)).thenReturn(Optional.of(r));
    }
    @Test void pagoAjenoNoSePuedeConsultar() {
        reserva(7,2);var p=new Pago(7L,BigDecimal.TEN,"ARS");when(pagosRepo.buscarPorId(9L)).thenReturn(Optional.of(p));
        var error=assertThrows(SeguridadException.class,()->pagos.consultarPago(9L));assertEquals(404,error.getEstado());assertEquals("Pago no disponible",error.getMessage());
    }
    @Test void pagoPropioSePuedeConsultar() {
        reserva(7,1);var p=new Pago(7L,BigDecimal.TEN,"ARS");when(pagosRepo.buscarPorId(9L)).thenReturn(Optional.of(p));assertEquals(BigDecimal.TEN,pagos.consultarPago(9L).monto());
    }
    @Test void cobrarReservaAjenaNoLlamaAPasarela() {
        reserva(7,2);assertThrows(SeguridadException.class,()->pagos.procesarPago(new PagoRequest(7L,BigDecimal.TEN,"ARS")));verifyNoInteractions(pasarela);verify(pagosRepo,never()).guardar(any());
    }
    @Test void loteMixtoSeRechazaCompletoAntesDeCobrar() {
        reserva(7,1);reserva(8,2);assertThrows(SeguridadException.class,()->pagos.procesarLote(new PagoLoteRequest(List.of(7L,8L),new BigDecimal("20"),"ARS")));verifyNoInteractions(pasarela);verify(pagosRepo,never()).guardar(any());
    }
    @Test void loteGrandeNoCargaReservasNiCobra() {
        var ids=java.util.stream.LongStream.range(1,22).boxed().toList();assertThrows(SeguridadException.class,()->pagos.procesarLote(new PagoLoteRequest(ids,BigDecimal.TEN,"ARS")));verifyNoInteractions(reservasRepo,pasarela);
    }
    @Test void montoMenorAlPrecioRealNoSeCobra() {
        reserva(7,1);assertThrows(com.stayhub.pagos.exception.PagoException.class,()->pagos.procesarPago(new PagoRequest(7L,BigDecimal.ONE,"ARS")));verifyNoInteractions(pasarela);
    }
    ReservaResponse respuesta(String estado) {
        return new ReservaResponse(7L,"DIRECTA",null,1L,"DOBLE",1,LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),"Ana","Paz","mail@test.com",BigDecimal.TEN,"ARS",estado,java.time.LocalDateTime.now());
    }
    @Test void pagoValidoSeApruebaConfirmaYPublicaUnaVez() throws Exception {
        ServicioDeReservas reservas=mock(ServicioDeReservas.class);campo(pagos,"reservas",reservas);
        when(reservas.consultarParaPago(7L)).thenReturn(respuesta("PENDIENTE"));when(reservas.confirmarReserva(7L)).thenReturn(respuesta("CONFIRMADA"));
        when(pasarela.cobrar(BigDecimal.TEN,"ARS","reserva-7")).thenReturn(new ResultadoCobro(true,"ref-qa",null));
        var resultado=pagos.procesarPago(new PagoRequest(7L,BigDecimal.TEN,"ARS","qa-payment-key-0001"));
        assertEquals("APROBADO",resultado.estado());assertEquals("ref-qa",resultado.referenciaPasarela());
        verify(reservas).confirmarReserva(7L);verify(publicador,times(1)).publicarPagoAprobado(any());
    }
    @Test void montoCeroReservaPagadaEIdempotenciaEvitanSegundoCobro() throws Exception {
        assertThrows(RuntimeException.class,()->pagos.procesarPago(new PagoRequest(7L,BigDecimal.ZERO,"ARS")));
        ServicioDeReservas reservas=mock(ServicioDeReservas.class);campo(pagos,"reservas",reservas);when(reservas.consultarParaPago(7L)).thenReturn(respuesta("PENDIENTE"));
        when(pagosRepo.existeAprobadoParaReserva(7L)).thenReturn(true);
        assertThrows(RuntimeException.class,()->pagos.procesarPago(new PagoRequest(7L,BigDecimal.TEN,"ARS")));
        var existente=new Pago(1L,List.of(7L),BigDecimal.TEN,"ARS","qa-payment-key-0002");existente.aprobar("ref-previa");campo(existente,"id",11L);
        when(pagosRepo.buscarPorClaveIdempotencia(1L,"qa-payment-key-0002")).thenReturn(Optional.of(existente));when(pagosRepo.buscarPorId(11L)).thenReturn(Optional.of(existente));when(reservas.consultarReserva(7L)).thenReturn(respuesta("CONFIRMADA"));
        assertEquals("ref-previa",pagos.procesarPago(new PagoRequest(7L,BigDecimal.TEN,"ARS","qa-payment-key-0002")).referenciaPasarela());
        verifyNoInteractions(pasarela);
    }
    @Test void rechazoDePasarelaNoInformaAprobacionNiConfirmaReserva() throws Exception {
        ServicioDeReservas reservas=mock(ServicioDeReservas.class);campo(pagos,"reservas",reservas);when(reservas.consultarParaPago(7L)).thenReturn(respuesta("PENDIENTE"));
        when(pasarela.cobrar(BigDecimal.TEN,"ARS","reserva-7")).thenReturn(new ResultadoCobro(false,null,"rechazado QA"));
        assertThrows(RuntimeException.class,()->pagos.procesarPago(new PagoRequest(7L,BigDecimal.TEN,"ARS")));
        verify(reservas,never()).confirmarReserva(any());verifyNoInteractions(publicador);verify(pagosRepo,times(2)).guardar(any());
    }
}
