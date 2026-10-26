package com.stayhub.servicioDeOverbooking.service;

import com.stayhub.servicioDeOverbooking.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.servicioDeOverbooking.dto.*;
import com.stayhub.servicioDeOverbooking.messaging.PublicadorSolicitudNotificacionAsincrona;
import com.stayhub.servicioDeOverbooking.model.EstrategiaResolucion;
import com.stayhub.servicioDeOverbooking.repository.ConflictoOverbookingRepository;
import com.stayhub.reservas.contrato.*;
import jakarta.enterprise.inject.Instance;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResolucionAdminTest {
    ServicioDeOverbookingImpl servicio;
    ServicioDeInventarioYTarifasPort lectura;
    GestionDeDisponibilidadPort cupo;
    ConflictoOverbookingRepository repo;
    PublicadorSolicitudNotificacionAsincrona notificaciones;
    LocalDate desde=LocalDate.now().plusDays(3), hasta=desde.plusDays(2);
    @BeforeEach void preparar() throws Exception {
        servicio=new ServicioDeOverbookingImpl();
        lectura=mock(ServicioDeInventarioYTarifasPort.class);cupo=mock(GestionDeDisponibilidadPort.class);
        repo=mock(ConflictoOverbookingRepository.class);notificaciones=mock(PublicadorSolicitudNotificacionAsincrona.class);
        for(var entry:java.util.Map.of("repositorio",repo,"publicadorNotificacion",notificaciones,"inventarioLectura",instance(lectura),"disponibilidad",instance(cupo)).entrySet()) {
            var field=ServicioDeOverbookingImpl.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(servicio,entry.getValue());
        }
    }
    <T> Instance<T> instance(T value) {
        Instance<T> i=mock(Instance.class);when(i.isResolvable()).thenReturn(true);when(i.get()).thenReturn(value);return i;
    }
    ConflictoReservaDTO solicitud() { return new ConflictoReservaDTO(13L,33L,"STANDARD",1,desde,hasta,"BOOKING","QA",null); }
    @Test void alternativaRetieneYConfirmaCupo() {
        when(lectura.consultarDisponibilidad(33L,desde,hasta)).thenReturn(List.of(new DisponibilidadDTO(33L,"SUITE",desde,hasta,2)));
        when(cupo.crearHold(33L,"SUITE",1,desde,hasta)).thenReturn("qa-hold");
        var r=servicio.resolverConflicto(solicitud());
        assertTrue(r.resuelto());assertEquals(EstrategiaResolucion.REUBICACION,r.estrategia());assertEquals("qa-hold",r.holdAlternativoId());
        verify(cupo).confirmarHold("qa-hold");verify(repo).guardar(any());verifyNoInteractions(notificaciones);
    }
    @Test void sinAlternativaResuelvePorCompensacionSinOcuparCupo() {
        when(lectura.consultarDisponibilidad(33L,desde,hasta)).thenReturn(List.of());
        var r=servicio.resolverConflicto(solicitud());
        assertTrue(r.resuelto());assertEquals(EstrategiaResolucion.COMPENSACION,r.estrategia());verifyNoInteractions(cupo,notificaciones);
    }
    @Test void alternativaAgotadaDuranteLaOperacionNoRompeElFlujo() {
        when(lectura.consultarDisponibilidad(33L,desde,hasta)).thenReturn(List.of(new DisponibilidadDTO(33L,"SUITE",desde,hasta,2)));
        when(cupo.crearHold(33L,"SUITE",1,desde,hasta)).thenThrow(new SinDisponibilidadException("Sin cupo"));
        var r=servicio.resolverConflicto(solicitud());assertEquals(EstrategiaResolucion.COMPENSACION,r.estrategia());verify(cupo,never()).confirmarHold(any());
    }
}
