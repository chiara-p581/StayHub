package com.stayhub.usuarios.service;

import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.messaging.EventoPassword;
import com.stayhub.usuarios.messaging.PublicadorEventoPassword;
import com.stayhub.usuarios.messaging.TipoEventoPassword;
import com.stayhub.usuarios.model.RolUsuario;
import com.stayhub.usuarios.model.Usuario;
import com.stayhub.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecuperacionPasswordTest {

    private void inyectar(Object o, String nombre, Object valor) throws Exception {
        var f = o.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        f.set(o, valor);
    }

    @Test
    void generaTokenPeroGuardaSoloSuHash() throws Exception {
        var repo = mock(UsuarioRepository.class);
        var pub = mock(PublicadorEventoPassword.class);
        var u = new Usuario("test@example.com", "hash", "Ana", "A", RolUsuario.HUESPED);
        when(repo.buscarPorEmail("test@example.com")).thenReturn(Optional.of(u));

        var s = new ServicioDeUsuariosImpl();
        inyectar(s, "repositorio", repo);
        inyectar(s, "publicadorPassword", pub);
        s.solicitarRecuperacionPassword("test@example.com");

        var a = ArgumentCaptor.forClass(EventoPassword.class);
        verify(pub).publicar(a.capture());
        String token = a.getValue().token();
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));

        // En la base queda el hash, nunca el token
        var f = Usuario.class.getDeclaredField("resetTokenHash");
        f.setAccessible(true);
        assertEquals(TokenRecuperacion.hash(token), f.get(u));
        assertNotEquals(token, f.get(u));

        // Un segundo pedido inmediato no genera otro mail
        s.solicitarRecuperacionPassword("test@example.com");
        verify(pub, times(1)).publicar(any());
    }

    @Test
    void tokenExpiradoNoCambiaLaPassword() throws Exception {
        var repo = mock(UsuarioRepository.class);
        var pub = mock(PublicadorEventoPassword.class);
        String token = TokenRecuperacion.generar();
        var u = new Usuario("test@example.com", "original", "Ana", "A", RolUsuario.HUESPED);
        u.generarTokenRecuperacion(TokenRecuperacion.hash(token), LocalDateTime.now().minusMinutes(1));
        when(repo.buscarPorResetTokenHash(TokenRecuperacion.hash(token))).thenReturn(Optional.of(u));

        var s = new ServicioDeUsuariosImpl();
        inyectar(s, "repositorio", repo);
        inyectar(s, "publicadorPassword", pub);

        assertThrows(UsuarioException.class, () -> s.resetearPassword(token, "passwordNueva"));
        assertEquals("original", u.getPasswordHash());
        verifyNoInteractions(pub);
    }

    @Test
    void resetValidoInvalidaTokenYPublicaAviso() throws Exception {
        var repo = mock(UsuarioRepository.class);
        var pub = mock(PublicadorEventoPassword.class);
        String token = TokenRecuperacion.generar();
        var u = new Usuario("test@example.com", "original", "Ana", "A", RolUsuario.HUESPED);
        u.generarTokenRecuperacion(TokenRecuperacion.hash(token), LocalDateTime.now().plusMinutes(30));
        when(repo.buscarPorResetTokenHash(TokenRecuperacion.hash(token))).thenReturn(Optional.of(u));

        var s = new ServicioDeUsuariosImpl();
        inyectar(s, "repositorio", repo);
        inyectar(s, "publicadorPassword", pub);
        inyectar(s, "passwordHasher", new PasswordHasher());

        s.resetearPassword(token, "passwordNueva");

        assertFalse(u.tokenVigente());
        assertTrue(new PasswordHasher().verificar("passwordNueva", u.getPasswordHash()));
        var a = ArgumentCaptor.forClass(EventoPassword.class);
        verify(pub).publicar(a.capture());
        assertEquals(TipoEventoPassword.PASSWORD_CAMBIADA, a.getValue().tipo());
        assertNull(a.getValue().token());
    }
}