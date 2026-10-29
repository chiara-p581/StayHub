package com.stayhub.usuarios.service;

import com.stayhub.usuarios.dto.*;
import com.stayhub.usuarios.messaging.*;
import com.stayhub.usuarios.model.*;
import com.stayhub.usuarios.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.stayhub.seguridad.IdentidadFixture.campo;

class ServicioDeUsuariosRegistroTest {
    ServicioDeUsuariosImpl servicio; UsuarioRepository repo; PublicadorEventoUsuario eventos; PasswordHasher hasher;
    @BeforeEach void preparar() throws Exception {servicio=new ServicioDeUsuariosImpl();repo=mock(UsuarioRepository.class);eventos=mock(PublicadorEventoUsuario.class);hasher=new PasswordHasher();campo(servicio,"repositorio",repo);campo(servicio,"publicadorEventos",eventos);campo(servicio,"publicadorPassword",mock(PublicadorEventoPassword.class));campo(servicio,"passwordHasher",hasher);}
    @Test void registraHuespedPublicaBienvenidaYPermiteAutenticarSinExponerHash(){
        var req=new RegistroUsuarioRequest("qa-registro@test.com","ClaveSegura.2026","QA","Registro",RolUsuario.HUESPED);
        var respuesta=servicio.registrar(req);assertEquals("HUESPED",respuesta.rol());assertEquals("qa-registro@test.com",respuesta.email());
        var captor=org.mockito.ArgumentCaptor.forClass(Usuario.class);verify(repo).guardar(captor.capture());verify(eventos).publicarUsuarioRegistrado(any());
        when(repo.buscarPorEmail(req.email())).thenReturn(Optional.of(captor.getValue()));assertEquals(req.email(),servicio.autenticar(new LoginRequest(req.email(),req.password())).email());
    }
    @Test void duplicadoPasswordDebilYCredencialIncorrectaSeRechazanSinPersistir(){
        when(repo.buscarPorEmail("duplicado@test.com")).thenReturn(Optional.of(new Usuario("duplicado@test.com",hasher.hash("ClaveSegura.2026"),"QA","Dup",RolUsuario.HUESPED)));
        assertThrows(RuntimeException.class,()->servicio.registrar(new RegistroUsuarioRequest("duplicado@test.com","ClaveSegura.2026","QA","Dup",RolUsuario.HUESPED)));
        assertThrows(RuntimeException.class,()->servicio.registrar(new RegistroUsuarioRequest("nuevo@test.com","corta","QA","Nuevo",RolUsuario.HUESPED)));
        assertThrows(RuntimeException.class,()->servicio.autenticar(new LoginRequest("duplicado@test.com","incorrecta")));
        verify(repo,never()).guardar(any());
    }
}
