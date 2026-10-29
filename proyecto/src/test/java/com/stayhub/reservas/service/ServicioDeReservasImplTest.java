package com.stayhub.reservas.service;
import com.stayhub.seguridad.*;
import com.stayhub.reservas.contrato.*;
import com.stayhub.reservas.dto.*;
import com.stayhub.reservas.model.*;
import com.stayhub.reservas.repository.*;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.canalesexternos.contrato.interno.SolicitudReserva;
import com.stayhub.canalesexternos.dto.HuespedDTO;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.UsuarioResponse;
import jakarta.enterprise.inject.Instance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.stayhub.seguridad.IdentidadFixture.*;

class ServicioDeReservasImplTest {
    ServicioDeReservasImpl servicio; ReservaRepository repo; PagoRepository pagos; GestionDeDisponibilidadPort cupo;
    ServicioDeUsuarios usuarios; ServicioDeInventarioYTarifas tarifas;
    LocalDate desde=LocalDate.now().plusDays(1),hasta=desde.plusDays(2);
    @BeforeEach void preparar() throws Exception {
        servicio=new ServicioDeReservasImpl();repo=mock(ReservaRepository.class);pagos=mock(PagoRepository.class);
        cupo=mock(GestionDeDisponibilidadPort.class);usuarios=mock(ServicioDeUsuarios.class);tarifas=mock(ServicioDeInventarioYTarifas.class);
        Instance<GestionDeDisponibilidadPort> instance=mock(Instance.class);when(instance.isResolvable()).thenReturn(true);when(instance.get()).thenReturn(cupo);
        campo(servicio,"repositorio",repo);campo(servicio,"pagos",pagos);campo(servicio,"usuarios",usuarios);campo(servicio,"tarifas",tarifas);
        campo(servicio,"disponibilidad",instance);campo(servicio,"identidad",usuario(1L,"HUESPED"));
        when(cupo.crearHold(any(),any(),anyInt(),any(),any())).thenReturn("hold-1");
        when(usuarios.buscarPorId(1L)).thenReturn(new UsuarioResponse(1L,"real@mail.com","Real","Cuenta","HUESPED"));
        when(tarifas.consultarTarifas(1L,desde,hasta)).thenReturn(List.of(new TarifaDTO(1L,"DOBLE",desde,hasta,new BigDecimal("150000"),"ARS")));
    }
    Reserva reserva(long owner) {
        Reserva r=new Reserva("DIRECTA",null,1L,"DOBLE",1,desde,hasta,new Huesped("Real","Cuenta","real@mail.com","123"),new BigDecimal("150000"),"ARS");
        r.asignarPropietario(owner);r.iniciarHold("hold-1");when(repo.buscarPorId(7L)).thenReturn(Optional.of(r));when(repo.buscarPorIdBloqueando(7L)).thenReturn(Optional.of(r));return r;
    }
    @Test void operacionesDeReservasTienenPermisoExplicitoDelContenedor() throws Exception {
        // Con permisos mezclados WildFly puede denegar métodos sin anotación antes
        // de que se ejecuten los controles de sesión y propiedad del servicio.
        for (var contrato : List.of(ServicioDeReservas.class, com.stayhub.canalesexternos.contrato.interno.ServicioDeReservasPort.class)) {
            for (var metodo : contrato.getMethods()) {
                assertNotNull(ServicioDeReservasImpl.class.getMethod(metodo.getName(), metodo.getParameterTypes())
                        .getAnnotation(jakarta.annotation.security.PermitAll.class), metodo.toString());
            }
        }
    }
    @Test void listadoPropioExigeSesion() throws Exception {
        campo(servicio,"identidad",usuario(null,"HUESPED"));
        assertThrows(SeguridadException.class,()->servicio.listarMias(0,100));
        verify(repo,never()).listarPorUsuario(any(),anyInt(),anyInt());
    }
    @Test void listadoPropioSeFiltraPorUsuarioDeSesion() {
        when(repo.listarPorUsuario(1L,0,100)).thenReturn(List.of());
        assertTrue(servicio.listarMias(0,100).isEmpty());
        verify(repo).listarPorUsuario(1L,0,100);
    }
    @Test void correoCoincidenteNoDaAccesoAReservaDeOtroId() {
        reserva(2);var error=assertThrows(SeguridadException.class,()->servicio.consultarReserva(7L));assertEquals(404,error.getEstado());
    }
    @Test void huespedNoPuedeCancelarReservaAjena() {
        Reserva r=reserva(2);assertThrows(SeguridadException.class,()->servicio.cancelarReserva(7L));assertEquals(EstadoReserva.PENDIENTE,r.getEstado());verifyNoInteractions(cupo);verify(repo,never()).guardar(any());
    }
    @Test void huespedNoPuedeConfirmarReservaAjenaInclusoConPago() {
        reserva(2);when(pagos.existeAprobadoParaReserva(7L)).thenReturn(true);assertThrows(SeguridadException.class,()->servicio.confirmarReserva(7L));verifyNoInteractions(cupo);
    }
    @Test void reservaPropiaNoSeConfirmaSinPago() {
        reserva(1);var error=assertThrows(SeguridadException.class,()->servicio.confirmarReserva(7L));assertEquals(409,error.getEstado());verifyNoInteractions(cupo);
    }
    @Test void reservaPropiaPagadaSeConfirma() {
        reserva(1);when(pagos.existeAprobadoParaReserva(7L)).thenReturn(true);assertEquals("CONFIRMADA",servicio.confirmarReserva(7L).estado());verify(cupo).confirmarHold("hold-1");
    }
    @Test void precioYDatosDeVictimaEnRequestNoSonAutoridad() {
        var request=new ReservaRequest(1L,"DOBLE",2,desde,hasta,"Victima","Ajena","victima@mail.com","123",BigDecimal.ZERO,"USD");
        var respuesta=servicio.crearReserva(request);assertEquals("real@mail.com",respuesta.huespedEmail());assertEquals("Real",respuesta.huespedNombre());assertEquals(new BigDecimal("300000"),respuesta.precioTotal());assertEquals("ARS",respuesta.moneda());
        verify(repo).guardar(argThat(r->r.getUsuarioId().equals(1L)));
    }
    @Test void crearReservaValidaCalculaPrecioRetieneCupoYPersisteUnaVez() {
        var request=new ReservaRequest(1L,"DOBLE",2,desde,hasta,"Ignorado","Ignorado","otro@mail.com","123",BigDecimal.ZERO,"USD");
        var respuesta=servicio.crearReserva(request);
        assertEquals("PENDIENTE",respuesta.estado());assertEquals(new BigDecimal("300000"),respuesta.precioTotal());assertEquals("ARS",respuesta.moneda());
        verify(cupo).crearHold(1L,"DOBLE",2,desde,hasta);verify(repo,times(1)).guardar(any());
    }
    @Test void fechasInvertidasYCantidadCeroSeRechazanAntesDeRetener() {
        assertThrows(SeguridadException.class,()->servicio.crearReserva(new ReservaRequest(1L,"DOBLE",1,hasta,desde,null,null,null,"123",null,null)));
        assertThrows(SeguridadException.class,()->servicio.crearReserva(new ReservaRequest(1L,"DOBLE",0,desde,hasta,null,null,null,"123",null,null)));
        verifyNoInteractions(cupo);verify(repo,never()).guardar(any());
    }
    @Test void faltaDeCupoNoConfirmaNiDejaDescuentoParcial() {
        when(cupo.crearHold(any(),any(),anyInt(),any(),any())).thenThrow(new SinDisponibilidadException("sin cupo"));
        var respuesta=servicio.crearReserva(new ReservaRequest(1L,"DOBLE",1,desde,hasta,null,null,null,"123",null,null));
        assertEquals("RECHAZADA",respuesta.estado());verify(cupo,never()).confirmarHold(any());verify(repo).guardar(any());
    }
    @Test void cuotaDePendientesCortaAntesDeReservarCupo() {
        when(repo.contarPendientesPorUsuario(1L)).thenReturn(10L);
        assertThrows(SeguridadException.class,()->servicio.crearReserva(new ReservaRequest(1L,"DOBLE",1,desde,hasta,null,null,null,"123",null,null)));verifyNoInteractions(cupo);
    }
    @Test void adminPuedeCancelarReservaAjena() throws Exception {
        reserva(2);campo(servicio,"identidad",usuario(9L,"ADMIN"));assertEquals("CANCELADA",servicio.cancelarReserva(7L).estado());
    }
    @Test void pmsNoPuedeCancelarReservaDeHotelNoPermitido() throws Exception {
        reserva(2);campo(servicio,"identidad",pms(Set.of(3L)));assertThrows(SeguridadException.class,()->servicio.cancelarReserva(7L));verifyNoInteractions(cupo);
    }
    @Test void pagoYModificacionNoPuedenReactivarReservaConfirmada() {
        Reserva r=reserva(1);r.confirmar("hold-1");assertThrows(SeguridadException.class,()->servicio.consultarParaPago(7L));
        var cambio=new ReservaRequest(1L,"DOBLE",1,desde,hasta,null,null,null,null,BigDecimal.ONE,"ARS");assertThrows(SeguridadException.class,()->servicio.modificarReserva(7L,cambio));verifyNoInteractions(cupo);
    }
    SolicitudReserva externa(String referencia) {
        return new SolicitudReserva(referencia,"BOOKING",1L,desde,hasta,"DOBLE",1,new HuespedDTO("Ana","Paz","ota@test.com","123"),new BigDecimal("150000"),"ARS");
    }
    @Test void altaOtaValidaEsIdempotenteYConfirmaUnaSolaReserva() {
        when(repo.buscarPorCanalYReferencia("BOOKING","ota-1")).thenReturn(Optional.empty());
        var resultado=servicio.crearDesdeCanal(externa("ota-1"));assertEquals("CONFIRMADA",resultado.estado());
        verify(cupo).confirmarHold("hold-1");verify(repo,times(1)).guardar(any());
    }
    @Test void modificacionYCancelacionOtaConservanReferenciaYNoRevivenCancelada() {
        Reserva r=new Reserva("BOOKING","ota-2",1L,"DOBLE",1,desde,hasta,new Huesped("Ana","Paz","ota@test.com","123"),new BigDecimal("150000"),"ARS");
        r.iniciarHold("hold-viejo");r.confirmar("hold-viejo");when(repo.buscarPorCanalYReferencia("BOOKING","ota-2")).thenReturn(Optional.of(r));
        when(cupo.reemplazarHold(any(),any(),any(),anyInt(),any(),any())).thenReturn("hold-nuevo");
        var modificada=servicio.modificarDesdeCanal(externa("ota-2"));assertEquals("CONFIRMADA",modificada.estado());assertEquals("ota-2",r.getReferenciaExterna());
        var cancelada=servicio.cancelarDesdeCanal("BOOKING","ota-2");assertEquals("CANCELADA",cancelada.estado());assertEquals("ota-2",r.getReferenciaExterna());
        verify(cupo).liberarHold("hold-nuevo");
    }
}
