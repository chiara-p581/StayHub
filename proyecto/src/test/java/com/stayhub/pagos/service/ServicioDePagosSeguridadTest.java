package com.stayhub.pagos.service;

import com.stayhub.seguridad.*;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.pagos.model.Pago;
import com.stayhub.pagos.dto.*;
import com.stayhub.pagos.client.pasarela.PasarelaDePagoClient;
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
    ServicioDePagosImpl pagos; ReservaRepository reservasRepo; PagoRepository pagosRepo; PasarelaDePagoClient pasarela;
    @BeforeEach void preparar() throws Exception {
        pagos=new ServicioDePagosImpl();reservasRepo=mock(ReservaRepository.class);pagosRepo=mock(PagoRepository.class);pasarela=mock(PasarelaDePagoClient.class);
        var reservas=new ServicioDeReservasImpl();var actor=usuario(1L,"HUESPED");
        campo(reservas,"identidad",actor);campo(reservas,"repositorio",reservasRepo);
        campo(pagos,"identidad",actor);campo(pagos,"repositorio",pagosRepo);campo(pagos,"reservas",reservas);campo(pagos,"pasarela",pasarela);
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
}
