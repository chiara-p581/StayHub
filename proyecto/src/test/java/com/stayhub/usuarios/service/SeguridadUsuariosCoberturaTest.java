package com.stayhub.usuarios.service;

import com.stayhub.usuarios.dto.UsuarioResponse;
import com.stayhub.usuarios.model.RolUsuario;
import com.stayhub.usuarios.model.Usuario;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de PasswordHasher, TokenRecuperacion, UsuarioMapper y el modelo Usuario. */
class SeguridadUsuariosCoberturaTest {
    private final PasswordHasher hasher = new PasswordHasher();

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    // ---------- PasswordHasher ----------

    @Test
    void hashUsaSaltAleatorioYDevuelveCuatroPartes() {
        String h1 = hasher.hash("ClaveSegura-2026");
        String h2 = hasher.hash("ClaveSegura-2026");

        assertNotEquals(h1, h2);
        String[] p = h1.split("\\$");
        assertEquals(4, p.length);
        assertEquals("pbkdf2-sha256", p[0]);
        assertEquals("310000", p[1]);
        assertEquals(16, Base64.getDecoder().decode(p[2]).length);
        assertEquals(32, Base64.getDecoder().decode(p[3]).length);
        assertTrue(hasher.verificar("ClaveSegura-2026", h2));
    }

    @Test
    void verificarEsSensibleAMayusculasYRechazaPasswordDistinta() {
        String h = hasher.hash("ClaveSegura-2026");
        assertFalse(hasher.verificar("clavesegura-2026", h));
        assertFalse(hasher.verificar("", h));
    }

    @Test
    void verificarConNulosDevuelveFalse() {
        String h = hasher.hash("ClaveSegura-2026");
        assertFalse(hasher.verificar(null, h));
        assertFalse(hasher.verificar("ClaveSegura-2026", null));
        assertFalse(hasher.verificar(null, null));
    }

    @Test
    void verificarRechazaFormatosVersionadosMalformados() {
        String salt = b64(new byte[16]);
        String clave = b64(new byte[32]);
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$310000$" + salt));            // faltan partes
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$310000$" + salt + "$" + clave + "$extra"));
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$abc$" + salt + "$" + clave)); // iteraciones no numericas
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$65535$" + salt + "$" + clave)); // debajo del minimo
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$2000001$" + salt + "$" + clave)); // sobre el maximo
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$310000$%%%$" + clave));          // base64 invalido
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$310000$" + b64(new byte[15]) + "$" + clave)); // salt corto
        assertFalse(hasher.verificar("x", "pbkdf2-sha256$310000$" + salt + "$" + b64(new byte[15])));  // hash corto
    }

    @Test
    void verificarRechazaFormatoLegacyMalformado() {
        assertFalse(hasher.verificar("x", "solo-una-parte"));
        assertFalse(hasher.verificar("x", "a:b:c"));
        assertFalse(hasher.verificar("x", "%%%:%%%"));
        assertFalse(hasher.verificar("x", b64(new byte[8]) + ":" + b64(new byte[16])));  // salt de largo incorrecto
        assertFalse(hasher.verificar("x", b64(new byte[16]) + ":" + b64(new byte[8])));  // clave de largo incorrecto
    }

    @Test
    void verificarAceptaHashVersionadoConIteracionesLegacyMinimas() throws Exception {
        byte[] salt = new byte[16];
        PBEKeySpec spec = new PBEKeySpec("OtraClave-2026".toCharArray(), salt, 65_536, 256);
        byte[] derivada = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String hash = "pbkdf2-sha256$65536$" + b64(salt) + "$" + b64(derivada);

        assertTrue(hasher.verificar("OtraClave-2026", hash));
        assertFalse(hasher.verificar("OtraClave-2027", hash));
        assertTrue(hasher.requiereActualizacion(hash));
    }

    @Test
    void verificarFormatoLegacyDistingueClaveCorrectaDeIncorrecta() throws Exception {
        byte[] salt = new byte[16];
        salt[0] = 7;
        PBEKeySpec spec = new PBEKeySpec("Anterior-2026".toCharArray(), salt, 65_536, 128);
        byte[] derivada = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String legacy = b64(salt) + ":" + b64(derivada);

        assertTrue(hasher.verificar("Anterior-2026", legacy));
        assertFalse(hasher.verificar("Anterior-2027", legacy));
    }

    @Test
    void requiereActualizacionDetectaHashesViejosNulosYMalformados() {
        String salt = b64(new byte[16]);
        String clave = b64(new byte[32]);
        assertTrue(hasher.requiereActualizacion(null));
        assertTrue(hasher.requiereActualizacion(salt + ":" + clave));
        assertTrue(hasher.requiereActualizacion("pbkdf2-sha256$abc$" + salt + "$" + clave));
        assertTrue(hasher.requiereActualizacion("pbkdf2-sha256$310000$" + salt));
        assertTrue(hasher.requiereActualizacion("pbkdf2-sha256$309999$" + salt + "$" + clave));
        assertFalse(hasher.requiereActualizacion("pbkdf2-sha256$310000$" + salt + "$" + clave));
        assertFalse(hasher.requiereActualizacion("pbkdf2-sha256$400000$" + salt + "$" + clave));
    }

    // ---------- TokenRecuperacion ----------

    @Test
    void generarProduceTokensUrlSafeDe43CaracteresYUnicos() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String t = TokenRecuperacion.generar();
            assertTrue(t.matches("[A-Za-z0-9_-]{43}"), t);
            assertTrue(vistos.add(t));
        }
    }

    @Test
    void hashDeTokenEsSha256HexDeterministico() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TokenRecuperacion.hash("abc"));
        assertEquals(TokenRecuperacion.hash("x"), TokenRecuperacion.hash("x"));
        assertNotEquals(TokenRecuperacion.hash("x"), TokenRecuperacion.hash("y"));
        assertEquals(64, TokenRecuperacion.hash("").length());
    }

    @Test
    void hashDeTokenNuloLanzaNullPointer() {
        assertThrows(NullPointerException.class, () -> TokenRecuperacion.hash(null));
    }

    // ---------- UsuarioMapper ----------

    @Test
    void mapperCopiaCamposYNombreDelRol() {
        Usuario u = new Usuario("a@b.com", "h", "Ana", "Perez", RolUsuario.ADMIN);
        UsuarioResponse r = UsuarioMapper.aResponse(u);
        assertNull(r.id());
        assertEquals("a@b.com", r.email());
        assertEquals("Ana", r.nombre());
        assertEquals("Perez", r.apellido());
        assertEquals("ADMIN", r.rol());
    }

    @Test
    void mapperFallaSiElUsuarioNoTieneRol() {
        Usuario sinRol = new Usuario("a@b.com", "h", "Ana", "Perez", null);
        assertThrows(NullPointerException.class, () -> UsuarioMapper.aResponse(sinRol));
    }

    // ---------- Usuario ----------

    @Test
    void usuarioNuevoNoTieneTokenVigenteNiRecuperacionReciente() {
        Usuario u = new Usuario("a@b.com", "h", "Ana", "Perez", RolUsuario.HUESPED);
        assertFalse(u.tokenVigente());
        assertFalse(u.recuperacionReciente());
        assertEquals("h", u.getPasswordHash());
        assertEquals(RolUsuario.HUESPED, u.getRol());
    }

    @Test
    void actualizarPerfilSoloReemplazaElHashSiSeProveeUno() {
        Usuario u = new Usuario("a@b.com", "h", "Ana", "Perez", RolUsuario.HUESPED);
        u.actualizarPerfil("c@d.com", "Beto", "Gomez", null);
        assertEquals("c@d.com", u.getEmail());
        assertEquals("Beto", u.getNombre());
        assertEquals("Gomez", u.getApellido());
        assertEquals("h", u.getPasswordHash());
        u.actualizarPerfil("c@d.com", "Beto", "Gomez", "nuevo");
        assertEquals("nuevo", u.getPasswordHash());
    }

    @Test
    void tokenVigenteDependeDelVencimiento() {
        Usuario u = new Usuario("a@b.com", "h", "Ana", "Perez", RolUsuario.HUESPED);
        u.generarTokenRecuperacion("hash", LocalDateTime.now().plusMinutes(1));
        assertTrue(u.tokenVigente());
        u.generarTokenRecuperacion("hash", LocalDateTime.now().minusMinutes(1));
        assertFalse(u.tokenVigente());
        u.generarTokenRecuperacion(null, LocalDateTime.now().plusMinutes(1));
        assertFalse(u.tokenVigente());
        u.generarTokenRecuperacion("hash", null);
        assertFalse(u.tokenVigente());
    }

    @Test
    void recuperacionRecienteSoloEnElPrimerMinutoDelToken() {
        Usuario u = new Usuario("a@b.com", "h", "Ana", "Perez", RolUsuario.HUESPED);
        u.generarTokenRecuperacion("hash", LocalDateTime.now().plusMinutes(60));
        assertTrue(u.recuperacionReciente());
        u.generarTokenRecuperacion("hash", LocalDateTime.now().plusMinutes(58));
        assertFalse(u.recuperacionReciente());
        u.generarTokenRecuperacion("hash", LocalDateTime.now().minusMinutes(5));
        assertFalse(u.recuperacionReciente());
    }

    @Test
    void limpiarTokenInvalidaTokenYRecuperacionReciente() {
        Usuario u = new Usuario("a@b.com", "h", "Ana", "Perez", RolUsuario.HUESPED);
        u.generarTokenRecuperacion("hash", LocalDateTime.now().plusMinutes(60));
        u.limpiarTokenRecuperacion();
        assertFalse(u.tokenVigente());
        assertFalse(u.recuperacionReciente());
    }

    @Test
    void rolesDisponibles() {
        assertEquals(2, RolUsuario.values().length);
        assertSame(RolUsuario.HUESPED, RolUsuario.valueOf("HUESPED"));
        assertSame(RolUsuario.ADMIN, RolUsuario.valueOf("ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> RolUsuario.valueOf("ROOT"));
    }
}
