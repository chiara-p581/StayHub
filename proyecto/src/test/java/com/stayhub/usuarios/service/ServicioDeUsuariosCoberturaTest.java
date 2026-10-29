package com.stayhub.usuarios.service;

import com.stayhub.seguridad.SeguridadException;
import com.stayhub.usuarios.dto.ActualizacionUsuarioRequest;
import com.stayhub.usuarios.dto.LoginRequest;
import com.stayhub.usuarios.dto.RegistroUsuarioRequest;
import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.exception.CodigoErrorUsuario;
import com.stayhub.usuarios.exception.UsuarioException;
import com.stayhub.usuarios.messaging.EventoPassword;
import com.stayhub.usuarios.messaging.EventoUsuarioRegistrado;
import com.stayhub.usuarios.messaging.PublicadorEventoPassword;
import com.stayhub.usuarios.messaging.PublicadorEventoUsuario;
import com.stayhub.usuarios.messaging.TipoEventoPassword;
import com.stayhub.usuarios.model.RolUsuario;
import com.stayhub.usuarios.model.Usuario;
import com.stayhub.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Pruebas de ServicioDeUsuariosImpl: los colaboradores (repositorio, hasher, publicadores) son simulados. */
class ServicioDeUsuariosCoberturaTest {
    private static final String PASS_OK = "ClaveSegura-2026";
    private static final String TOKEN_OK = "A".repeat(43);

    private UsuarioRepository repo;
    private PasswordHasher hasher;
    private PublicadorEventoUsuario pubUsuario;
    private PublicadorEventoPassword pubPassword;
    private ServicioDeUsuariosImpl servicio;

    @BeforeEach
    void preparar() throws Exception {
        repo = mock(UsuarioRepository.class);
        hasher = mock(PasswordHasher.class);
        pubUsuario = mock(PublicadorEventoUsuario.class);
        pubPassword = mock(PublicadorEventoPassword.class);
        servicio = new ServicioDeUsuariosImpl();
        inyectar("repositorio", repo);
        inyectar("passwordHasher", hasher);
        inyectar("publicadorEventos", pubUsuario);
        inyectar("publicadorPassword", pubPassword);
        when(hasher.hash(anyString())).thenAnswer(i -> "HASH(" + i.getArgument(0) + ")");
        when(repo.guardar(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void inyectar(String campo, Object valor) throws Exception {
        Field f = ServicioDeUsuariosImpl.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(servicio, valor);
    }

    private static Usuario usuario(Long id, String email) throws Exception {
        Usuario u = new Usuario(email, "hashViejo", "Ana", "Perez", RolUsuario.HUESPED);
        Field f = Usuario.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(u, id);
        return u;
    }

    private static RegistroUsuarioRequest registro(String email, String pass, String nombre, String apellido, RolUsuario rol) {
        return new RegistroUsuarioRequest(email, pass, nombre, apellido, rol);
    }

    private static UsuarioException assertUsuario(CodigoErrorUsuario codigo, Executable e) {
        UsuarioException ex = assertThrows(UsuarioException.class, e);
        assertEquals(codigo, ex.getCodigo());
        return ex;
    }

    // ---------- registrar ----------

    @Test
    void registrarGuardaConHashPublicaEventoYDevuelveRespuesta() {
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.empty());

        UsuarioResponse r = servicio.registrar(registro("ana@mail.com", PASS_OK, "Ana", "Perez", RolUsuario.ADMIN));

        assertEquals("ana@mail.com", r.email());
        assertEquals("Ana", r.nombre());
        assertEquals("Perez", r.apellido());
        assertEquals("ADMIN", r.rol());
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).guardar(guardado.capture());
        assertEquals("HASH(" + PASS_OK + ")", guardado.getValue().getPasswordHash());
        ArgumentCaptor<EventoUsuarioRegistrado> ev = ArgumentCaptor.forClass(EventoUsuarioRegistrado.class);
        verify(pubUsuario).publicarUsuarioRegistrado(ev.capture());
        assertEquals("ana@mail.com", ev.getValue().email());
        assertEquals("Ana", ev.getValue().nombre());
        assertEquals("ADMIN", ev.getValue().rol());
    }

    @Test
    void registrarConEmailExistenteLanzaConflictoSinHashearNiPublicar() throws Exception {
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(usuario(1L, "ana@mail.com")));

        assertUsuario(CodigoErrorUsuario.EMAIL_YA_REGISTRADO,
                () -> servicio.registrar(registro("ana@mail.com", PASS_OK, "Ana", "P", RolUsuario.HUESPED)));

        verify(repo, never()).guardar(any());
        verifyNoInteractions(pubUsuario);
        verify(hasher, never()).hash(anyString());
    }

    @Test
    void registrarSolicitudNulaEsSeguridad400() {
        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.registrar(null));
        assertEquals(400, ex.getEstado());
        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba", "a@b", "a b@c.com", "@x.com"})
    void registrarRechazaEmailsMalformados(String email) {
        assertThrows(SeguridadException.class,
                () -> servicio.registrar(registro(email, PASS_OK, "Ana", "P", RolUsuario.HUESPED)));
        verify(repo, never()).guardar(any());
    }

    @Test
    void registrarRechazaCamposObligatoriosVaciosOExcedidos() {
        assertThrows(SeguridadException.class, () -> servicio.registrar(registro(null, PASS_OK, "Ana", "P", RolUsuario.HUESPED)));
        assertThrows(SeguridadException.class, () -> servicio.registrar(registro("a@b.com", null, "Ana", "P", RolUsuario.HUESPED)));
        assertThrows(SeguridadException.class, () -> servicio.registrar(registro("a@b.com", PASS_OK, " ", "P", RolUsuario.HUESPED)));
        assertThrows(SeguridadException.class, () -> servicio.registrar(registro("a@b.com", PASS_OK, "Ana", null, RolUsuario.HUESPED)));
        assertThrows(SeguridadException.class, () -> servicio.registrar(registro("a@b.com", PASS_OK, "n".repeat(81), "P", RolUsuario.HUESPED)));
        assertThrows(SeguridadException.class, () -> servicio.registrar(registro("a@b.com", PASS_OK, "Ana", "p".repeat(81), RolUsuario.HUESPED)));
        assertThrows(SeguridadException.class, () -> servicio.registrar(registro("a".repeat(120) + "@b.com", PASS_OK, "Ana", "P", RolUsuario.HUESPED)));
        verifyNoInteractions(repo);
    }

    @Test
    void registrarSinRolEsSolicitudInvalida() {
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                () -> servicio.registrar(registro("a@b.com", PASS_OK, "Ana", "P", null)));
        verify(repo, never()).guardar(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta1234", "12345678901", "password1234", "PASSWORD1234", "contraseña123", "123456789012", "qwertyuiop12"})
    void registrarRechazaPasswordsDebiles(String pass) {
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                () -> servicio.registrar(registro("a@b.com", pass, "Ana", "P", RolUsuario.HUESPED)));
        verify(repo, never()).guardar(any());
        verifyNoInteractions(pubUsuario);
    }

    @Test
    void registrarAceptaPasswordDeLongitudMinimaYMaximaYRechazaMayor() {
        when(repo.buscarPorEmail(anyString())).thenReturn(Optional.empty());
        assertEquals("a@b.com", servicio.registrar(registro("a@b.com", "x".repeat(12), "Ana", "P", RolUsuario.HUESPED)).email());
        assertEquals("c@b.com", servicio.registrar(registro("c@b.com", "x".repeat(128), "Ana", "P", RolUsuario.HUESPED)).email());
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                () -> servicio.registrar(registro("d@b.com", "x".repeat(129), "Ana", "P", RolUsuario.HUESPED)));
        verify(repo, times(2)).guardar(any());
    }

    // ---------- autenticar ----------

    @Test
    void autenticarConCredencialesValidasDevuelveElUsuarioSinReescribirHash() throws Exception {
        Usuario u = usuario(5L, "ana@mail.com");
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));
        when(hasher.verificar(PASS_OK, "hashViejo")).thenReturn(true);
        when(hasher.requiereActualizacion("hashViejo")).thenReturn(false);

        UsuarioResponse r = servicio.autenticar(new LoginRequest("ana@mail.com", PASS_OK));

        assertEquals(5L, r.id());
        assertEquals("HUESPED", r.rol());
        verify(repo, never()).guardar(any());
        verify(hasher, never()).hash(anyString());
    }

    @Test
    void autenticarMigraElHashLegacyTrasLoginExitoso() throws Exception {
        Usuario u = usuario(5L, "ana@mail.com");
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));
        when(hasher.verificar(PASS_OK, "hashViejo")).thenReturn(true);
        when(hasher.requiereActualizacion("hashViejo")).thenReturn(true);

        servicio.autenticar(new LoginRequest("ana@mail.com", PASS_OK));

        assertEquals("HASH(" + PASS_OK + ")", u.getPasswordHash());
        assertEquals("Ana", u.getNombre());
        assertEquals("ana@mail.com", u.getEmail());
        verify(repo).guardar(u);
    }

    @Test
    void autenticarConPasswordIncorrectaFallaSinMigrar() throws Exception {
        Usuario u = usuario(5L, "ana@mail.com");
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));
        when(hasher.verificar("mala", "hashViejo")).thenReturn(false);

        UsuarioException ex = assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS,
                () -> servicio.autenticar(new LoginRequest("ana@mail.com", "mala")));

        assertEquals("Email o contraseña incorrectos", ex.getMessage());
        verify(repo, never()).guardar(any());
        verify(hasher, never()).requiereActualizacion(anyString());
    }

    @Test
    void autenticarConEmailInexistenteUsaElMismoMensajeQueConPasswordMala() {
        when(repo.buscarPorEmail("nadie@mail.com")).thenReturn(Optional.empty());

        UsuarioException ex = assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS,
                () -> servicio.autenticar(new LoginRequest("nadie@mail.com", PASS_OK)));

        assertEquals("Email o contraseña incorrectos", ex.getMessage());
        verifyNoInteractions(hasher);
    }

    @Test
    void autenticarRechazaEntradasNulasVaciasOExcedidasSinConsultarElRepositorio() {
        assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS, () -> servicio.autenticar(null));
        assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS, () -> servicio.autenticar(new LoginRequest(null, PASS_OK)));
        assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS, () -> servicio.autenticar(new LoginRequest("  ", PASS_OK)));
        assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS, () -> servicio.autenticar(new LoginRequest("a@b.com", null)));
        assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS, () -> servicio.autenticar(new LoginRequest("a@b.com", "")));
        assertUsuario(CodigoErrorUsuario.CREDENCIALES_INVALIDAS, () -> servicio.autenticar(new LoginRequest("a@b.com", "x".repeat(129))));
        verifyNoInteractions(repo);
    }

    @Test
    void autenticarConEmailMuyLargoEsSeguridad400() {
        SeguridadException ex = assertThrows(SeguridadException.class,
                () -> servicio.autenticar(new LoginRequest("a".repeat(121), PASS_OK)));
        assertEquals(400, ex.getEstado());
        verifyNoInteractions(repo);
    }

    @Test
    void autenticarAceptaPasswordDeExactamente128Caracteres() throws Exception {
        String pass = "x".repeat(128);
        Usuario u = usuario(5L, "ana@mail.com");
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));
        when(hasher.verificar(pass, "hashViejo")).thenReturn(true);

        assertEquals(5L, servicio.autenticar(new LoginRequest("ana@mail.com", pass)).id());
    }

    // ---------- buscarPorId ----------

    @Test
    void buscarPorIdDevuelveElUsuarioMapeado() throws Exception {
        when(repo.buscarPorId(7L)).thenReturn(Optional.of(usuario(7L, "ana@mail.com")));

        UsuarioResponse r = servicio.buscarPorId(7L);

        assertEquals(7L, r.id());
        assertEquals("ana@mail.com", r.email());
        assertEquals("Perez", r.apellido());
    }

    @Test
    void buscarPorIdInexistenteLanzaNoEncontradoConElId() {
        when(repo.buscarPorId(9L)).thenReturn(Optional.empty());

        UsuarioException ex = assertUsuario(CodigoErrorUsuario.USUARIO_NO_ENCONTRADO, () -> servicio.buscarPorId(9L));

        assertTrue(ex.getMessage().contains("9"));
    }

    // ---------- actualizarPerfil ----------

    @Test
    void actualizarPerfilSinPasswordCambiaDatosYNoAvisa() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        when(repo.buscarPorId(3L)).thenReturn(Optional.of(u));
        when(repo.buscarPorEmail("nuevo@mail.com")).thenReturn(Optional.empty());

        UsuarioResponse r = servicio.actualizarPerfil(3L,
                new ActualizacionUsuarioRequest(" nuevo@mail.com ".trim(), " Beto ", " Gomez ", null));

        assertEquals("nuevo@mail.com", r.email());
        assertEquals("Beto", u.getNombre());
        assertEquals("Gomez", u.getApellido());
        assertEquals("hashViejo", u.getPasswordHash());
        verify(hasher, never()).hash(anyString());
        verifyNoInteractions(pubPassword);
    }

    @Test
    void actualizarPerfilConPasswordEnBlancoLaIgnora() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        when(repo.buscarPorId(3L)).thenReturn(Optional.of(u));
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));

        servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("ana@mail.com", "Ana", "Perez", "   "));

        assertEquals("hashViejo", u.getPasswordHash());
        verifyNoInteractions(pubPassword);
    }

    @Test
    void actualizarPerfilConPasswordNuevaHasheaInvalidaTokenYAvisa() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        u.generarTokenRecuperacion("h", LocalDateTime.now().plusMinutes(30));
        when(repo.buscarPorId(3L)).thenReturn(Optional.of(u));
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));

        servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("ana@mail.com", "Ana", "Perez", PASS_OK));

        assertEquals("HASH(" + PASS_OK + ")", u.getPasswordHash());
        assertFalse(u.tokenVigente());
        ArgumentCaptor<EventoPassword> ev = ArgumentCaptor.forClass(EventoPassword.class);
        verify(pubPassword).publicar(ev.capture());
        assertEquals(TipoEventoPassword.PASSWORD_CAMBIADA, ev.getValue().tipo());
        assertNull(ev.getValue().token());
        assertEquals(3L, ev.getValue().usuarioId());
        assertEquals("ana@mail.com", ev.getValue().email());
    }

    @Test
    void actualizarPerfilConEmailDeOtroUsuarioEsConflicto() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        Usuario otro = usuario(4L, "otro@mail.com");
        when(repo.buscarPorId(3L)).thenReturn(Optional.of(u));
        when(repo.buscarPorEmail("otro@mail.com")).thenReturn(Optional.of(otro));

        assertUsuario(CodigoErrorUsuario.EMAIL_YA_REGISTRADO,
                () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("otro@mail.com", "Ana", "P", null)));

        assertEquals("ana@mail.com", u.getEmail());
        verify(repo, never()).guardar(any());
    }

    @Test
    void actualizarPerfilDeUsuarioInexistenteFalla() {
        when(repo.buscarPorId(3L)).thenReturn(Optional.empty());
        assertUsuario(CodigoErrorUsuario.USUARIO_NO_ENCONTRADO,
                () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("a@b.com", "Ana", "P", null)));
    }

    @Test
    void actualizarPerfilNuloEsSeguridad400() {
        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.actualizarPerfil(3L, null));
        assertEquals(400, ex.getEstado());
    }

    @Test
    void actualizarPerfilRechazaCamposInvalidos() {
        assertThrows(SeguridadException.class, () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("malo", "Ana", "P", null)));
        assertThrows(SeguridadException.class, () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("a@b.com", " ", "P", null)));
        assertThrows(SeguridadException.class, () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("a@b.com", "Ana", null, null)));
        assertThrows(SeguridadException.class, () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("a@b.com", "n".repeat(81), "P", null)));
        assertThrows(SeguridadException.class, () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("a@b.com", "Ana", "P", "x".repeat(1025))));
        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta", "password1234", "PASSWORD1234"})
    void actualizarPerfilRechazaPasswordNuevaDebil(String pass) {
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                () -> servicio.actualizarPerfil(3L, new ActualizacionUsuarioRequest("a@b.com", "Ana", "P", pass)));
        verify(repo, never()).guardar(any());
        verifyNoInteractions(pubPassword);
    }

    // ---------- solicitarRecuperacionPassword ----------

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "sin-arroba", "a@b", "a b@c.com"})
    void solicitarRecuperacionRechazaEmailsInvalidos(String email) {
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.solicitarRecuperacionPassword(email));
        verifyNoInteractions(repo);
        verifyNoInteractions(pubPassword);
    }

    @Test
    void solicitarRecuperacionRechazaNuloYEmailDemasiadoLargo() {
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.solicitarRecuperacionPassword(null));
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                () -> servicio.solicitarRecuperacionPassword("a".repeat(117) + "@b.c"));
        verifyNoInteractions(repo);
    }

    @Test
    void solicitarRecuperacionDeEmailInexistenteNoHaceNadaNiFalla() {
        when(repo.buscarPorEmail("nadie@mail.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> servicio.solicitarRecuperacionPassword("nadie@mail.com"));

        verify(repo, never()).guardar(any());
        verifyNoInteractions(pubPassword);
    }

    @Test
    void solicitarRecuperacionGeneraTokenVigenteDeUnaHoraYPublicaElToken() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));

        servicio.solicitarRecuperacionPassword("ana@mail.com");

        assertTrue(u.tokenVigente());
        assertTrue(u.recuperacionReciente());
        verify(repo).guardar(u);
        ArgumentCaptor<EventoPassword> ev = ArgumentCaptor.forClass(EventoPassword.class);
        verify(pubPassword).publicar(ev.capture());
        assertEquals(TipoEventoPassword.SOLICITUD_RECUPERACION, ev.getValue().tipo());
        assertEquals(3L, ev.getValue().usuarioId());
        assertEquals("Ana", ev.getValue().nombre());
        assertEquals(43, ev.getValue().token().length());
    }

    @Test
    void solicitarRecuperacionRecienteNoGeneraOtroToken() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        u.generarTokenRecuperacion("hashPrevio", LocalDateTime.now().plusMinutes(60));
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));

        servicio.solicitarRecuperacionPassword("ana@mail.com");

        verify(repo, never()).guardar(any());
        verifyNoInteractions(pubPassword);
    }

    @Test
    void solicitarRecuperacionConTokenViejoGeneraUnoNuevo() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        u.generarTokenRecuperacion("hashPrevio", LocalDateTime.now().plusMinutes(10));
        when(repo.buscarPorEmail("ana@mail.com")).thenReturn(Optional.of(u));

        servicio.solicitarRecuperacionPassword("ana@mail.com");

        verify(pubPassword).publicar(any(EventoPassword.class));
        assertTrue(u.recuperacionReciente());
    }

    // ---------- resetearPassword ----------

    @Test
    void resetearPasswordConTokenVigenteCambiaHashLimpiaTokenYAvisa() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        u.generarTokenRecuperacion(TokenRecuperacion.hash(TOKEN_OK), LocalDateTime.now().plusMinutes(5));
        when(repo.buscarPorResetTokenHash(TokenRecuperacion.hash(TOKEN_OK))).thenReturn(Optional.of(u));

        servicio.resetearPassword(TOKEN_OK, PASS_OK);

        assertEquals("HASH(" + PASS_OK + ")", u.getPasswordHash());
        assertFalse(u.tokenVigente());
        verify(repo).guardar(u);
        ArgumentCaptor<EventoPassword> ev = ArgumentCaptor.forClass(EventoPassword.class);
        verify(pubPassword).publicar(ev.capture());
        assertEquals(TipoEventoPassword.PASSWORD_CAMBIADA, ev.getValue().tipo());
    }

    @Test
    void resetearPasswordConTokenVencidoFallaYNoTocaNada() throws Exception {
        Usuario u = usuario(3L, "ana@mail.com");
        u.generarTokenRecuperacion(TokenRecuperacion.hash(TOKEN_OK), LocalDateTime.now().minusSeconds(1));
        when(repo.buscarPorResetTokenHash(TokenRecuperacion.hash(TOKEN_OK))).thenReturn(Optional.of(u));

        UsuarioException ex = assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA,
                () -> servicio.resetearPassword(TOKEN_OK, PASS_OK));

        assertTrue(ex.getMessage().contains("expir"));
        assertEquals("hashViejo", u.getPasswordHash());
        verify(repo, never()).guardar(any());
        verifyNoInteractions(pubPassword);
    }

    @Test
    void resetearPasswordConTokenDesconocidoFalla() {
        when(repo.buscarPorResetTokenHash(anyString())).thenReturn(Optional.empty());

        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.resetearPassword(TOKEN_OK, PASS_OK));

        verify(repo, never()).guardar(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corto", "A", "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void resetearPasswordRechazaTokensConFormatoInvalido(String token) {
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.resetearPassword(token, PASS_OK));
        verifyNoInteractions(repo);
    }

    @Test
    void resetearPasswordRechazaTokenNuloYPasswordsDebiles() {
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.resetearPassword(null, PASS_OK));
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.resetearPassword(TOKEN_OK, null));
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.resetearPassword(TOKEN_OK, "corta"));
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.resetearPassword(TOKEN_OK, "qwertyuiop12"));
        assertUsuario(CodigoErrorUsuario.SOLICITUD_INVALIDA, () -> servicio.resetearPassword(TOKEN_OK, "x".repeat(129)));
        verifyNoInteractions(repo);
    }
}
