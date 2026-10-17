package com.stayhub.usuarios.service;

import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.exception.CodigoErrorUsuario;
import com.stayhub.usuarios.model.RolUsuario;
import com.stayhub.usuarios.model.Usuario;
import com.stayhub.usuarios.repository.UsuarioRepository;
import jakarta.ejb.ApplicationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginCompatibilidadTest {
    private ServicioDeUsuariosImpl servicio(UsuarioRepository repo) throws Exception {
        var servicio = new ServicioDeUsuariosImpl();
        for (var nombre : new String[]{"repositorio", "passwordHasher"}) {
            var campo = ServicioDeUsuariosImpl.class.getDeclaredField(nombre);
            campo.setAccessible(true);
            campo.set(servicio, nombre.equals("repositorio") ? repo : new PasswordHasher());
        }
        return servicio;
    }
    @Test void cuentaAnteriorPuedeIngresarConSuPasswordCortaCorrecta() throws Exception {
        var repo = mock(UsuarioRepository.class);
        var u = new Usuario("admin@example.test", new PasswordHasher().hash("demo1234"), "Admin", "Prueba", RolUsuario.ADMIN);
        when(repo.buscarPorEmail(u.getEmail())).thenReturn(Optional.of(u));
        assertEquals("ADMIN", servicio(repo).autenticar(new LoginRequest(u.getEmail(), "demo1234")).rol());
    }
    @Test void passwordCortaIncorrectaSigueSiendoRechazada() throws Exception {
        var repo = mock(UsuarioRepository.class);
        var u = new Usuario("admin@example.test", new PasswordHasher().hash("demo1234"), "Admin", "Prueba", RolUsuario.ADMIN);
        when(repo.buscarPorEmail(u.getEmail())).thenReturn(Optional.of(u));
        var error = assertThrows(UsuarioException.class, () -> servicio(repo).autenticar(new LoginRequest(u.getEmail(), "otra1234")));
        assertEquals(CodigoErrorUsuario.CREDENCIALES_INVALIDAS, error.getCodigo());
    }
    @Test void contrasenasNuevasConservanElMinimoDeDoce() throws Exception {
        var repo = mock(UsuarioRepository.class);
        assertThrows(UsuarioException.class, () -> servicio(repo).registrar(new RegistroUsuarioRequest("ana@example.test", "demo1234", "Ana", "Prueba", RolUsuario.HUESPED)));
        verify(repo, never()).guardar(any());
    }
    @Test void rechazosDeNegocioLleganAlMapperSinConvertirseEnErrorInterno() {
        var annotation = UsuarioException.class.getAnnotation(ApplicationException.class);
        assertNotNull(annotation);
        assertTrue(annotation.rollback());
    }
}
