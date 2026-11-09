package com.stayhub.infraestructura;

import com.stayhub.usuarios.service.*;
import com.stayhub.usuarios.repository.UsuarioRepository;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.model.*;
import com.stayhub.usuarios.messaging.*;
import com.stayhub.servicioDeOverbooking.service.ServicioDeOverbookingImpl;
import com.stayhub.servicioDeOverbooking.repository.ConflictoOverbookingRepository;
import com.stayhub.servicioDeOverbooking.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.servicioDeOverbooking.dto.ConflictoReservaDTO;
import com.stayhub.servicioDeOverbooking.messaging.PublicadorSolicitudNotificacionAsincrona;
import jakarta.enterprise.inject.Instance;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Propagación por cada productor real. El rollback persistente se verifica aparte con la sonda XA. */
class FallosProductoresObligatoriosTest {
    private static void inject(Object target, String field, Object value) throws Exception {
        var f=target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target,value);
    }
    private ServicioDeUsuariosImpl users(UsuarioRepository repo, PublicadorEventoUsuario events, PublicadorEventoPassword passwords) throws Exception {
        var service=new ServicioDeUsuariosImpl();
        inject(service,"repositorio",repo); inject(service,"passwordHasher",new PasswordHasher());
        inject(service,"publicadorEventos",events); inject(service,"publicadorPassword",passwords);
        return service;
    }
    private RegistroUsuarioRequest registration() {
        return new RegistroUsuarioRequest("qa@test.invalid","ClavePrueba.2026!","QA","Prueba",RolUsuario.HUESPED);
    }
    @Test void registroNoPublicaSiFallaPersistencia() throws Exception {
        var repo=mock(UsuarioRepository.class);var events=mock(PublicadorEventoUsuario.class);
        var failure=new IllegalStateException("persistencia controlada");
        when(repo.guardar(any())).thenThrow(failure);
        assertSame(failure,assertThrows(IllegalStateException.class,()->users(repo,events,mock(PublicadorEventoPassword.class)).registrar(registration())));
        verifyNoInteractions(events);
    }
    @Test void registroPropagaFalloDelEventoObligatorioDespuesDeGuardar() throws Exception {
        var repo=mock(UsuarioRepository.class);var events=mock(PublicadorEventoUsuario.class);
        var failure=new IllegalStateException("broker controlado");
        doThrow(failure).when(events).publicarUsuarioRegistrado(any());
        assertSame(failure,assertThrows(IllegalStateException.class,()->users(repo,events,mock(PublicadorEventoPassword.class)).registrar(registration())));
        var order=inOrder(repo,events);order.verify(repo).guardar(any());order.verify(events).publicarUsuarioRegistrado(any());
    }
    @Test void recuperacionPropagaFalloDespuesDePersistirHashDeToken() throws Exception {
        var repo=mock(UsuarioRepository.class);var events=mock(PublicadorEventoPassword.class);
        var user=new Usuario("qa@test.invalid","hash","QA","Prueba",RolUsuario.HUESPED);
        when(repo.buscarPorEmail(user.getEmail())).thenReturn(Optional.of(user));
        var failure=new IllegalStateException("broker controlado");doThrow(failure).when(events).publicar(any());
        assertSame(failure,assertThrows(IllegalStateException.class,()->users(repo,mock(PublicadorEventoUsuario.class),events).solicitarRecuperacionPassword(user.getEmail())));
        var order=inOrder(repo,events);order.verify(repo).guardar(user);order.verify(events).publicar(any());
    }
    @Test void resetPropagaFalloDelAvisoDespuesDeCambiarPassword() throws Exception {
        var repo=mock(UsuarioRepository.class);var events=mock(PublicadorEventoPassword.class);
        var user=new Usuario("qa@test.invalid","hash","QA","Prueba",RolUsuario.HUESPED);
        String token=TokenRecuperacion.generar();user.generarTokenRecuperacion(TokenRecuperacion.hash(token),LocalDateTime.now().plusHours(1));
        when(repo.buscarPorResetTokenHash(TokenRecuperacion.hash(token))).thenReturn(Optional.of(user));
        var failure=new IllegalStateException("broker controlado");doThrow(failure).when(events).publicar(any());
        assertSame(failure,assertThrows(IllegalStateException.class,()->users(repo,mock(PublicadorEventoUsuario.class),events).resetearPassword(token,"NuevaClave.2026!")));
        var order=inOrder(repo,events);order.verify(repo).guardar(user);order.verify(events).publicar(any());
    }
    @Test @SuppressWarnings("unchecked") void overbookingPropagaFalloDelAvisoObligatorio() throws Exception {
        var service=new ServicioDeOverbookingImpl();var repo=mock(ConflictoOverbookingRepository.class);
        var events=mock(PublicadorSolicitudNotificacionAsincrona.class);
        var port=mock(ServicioDeInventarioYTarifasPort.class);Instance<ServicioDeInventarioYTarifasPort> instance=mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);when(instance.get()).thenReturn(port);
        inject(service,"repositorio",repo);inject(service,"publicadorNotificacion",events);inject(service,"inventarioLectura",instance);
        var failure=new IllegalStateException("broker controlado");doThrow(failure).when(events).publicar(any());
        var dto=new ConflictoReservaDTO(999L,2L,"STANDARD",1,LocalDate.now().plusDays(1),LocalDate.now().plusDays(3),"BOOKING","qa-fallo","qa@test.invalid");
        assertSame(failure,assertThrows(IllegalStateException.class,()->service.resolverConflicto(dto)));
        var order=inOrder(repo,events);order.verify(repo).guardar(any());order.verify(events).publicar(any());
    }
}
