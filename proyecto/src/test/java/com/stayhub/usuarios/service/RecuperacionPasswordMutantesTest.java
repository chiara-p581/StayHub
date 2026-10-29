package com.stayhub.usuarios.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.stayhub.usuarios.exception.CodigoErrorUsuario;
import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.messaging.PublicadorEventoPassword;
import com.stayhub.usuarios.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Límite de longitud (120) del email en solicitarRecuperacionPassword. */
class RecuperacionPasswordMutantesTest {
    UsuarioRepository repo;
    ServicioDeUsuariosImpl servicio;

    @BeforeEach void preparar() throws Exception {
        repo = mock(UsuarioRepository.class);
        servicio = new ServicioDeUsuariosImpl();
        var f = ServicioDeUsuariosImpl.class.getDeclaredField("repositorio");
        f.setAccessible(true);
        f.set(servicio, repo);
        var p = ServicioDeUsuariosImpl.class.getDeclaredField("publicadorPassword");
        p.setAccessible(true);
        p.set(servicio, mock(PublicadorEventoPassword.class));
        when(repo.buscarPorEmail(anyString())).thenReturn(Optional.empty());
    }

    private String emailDeLongitud(int n) {
        String email = "a".repeat(n - "@b.com".length()) + "@b.com";
        assertEquals(n, email.length());
        return email;
    }

    @Test void emailDeExactamente120CaracteresSeProcesa() {
        String email = emailDeLongitud(120);
        assertDoesNotThrow(() -> servicio.solicitarRecuperacionPassword(email));
        verify(repo).buscarPorEmail(email);
    }

    @Test void emailDe121CaracteresEsSolicitudInvalidaYNoConsultaElRepositorio() {
        var error = assertThrows(UsuarioException.class,
                () -> servicio.solicitarRecuperacionPassword(emailDeLongitud(121)));
        assertEquals(CodigoErrorUsuario.SOLICITUD_INVALIDA, error.getCodigo());
        verifyNoInteractions(repo);
    }
}
