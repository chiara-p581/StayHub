package com.stayhub.usuarios.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void generaHashVersionadoYVerificable() {
        String hash = hasher.hash("UnaPasswordSegura-2026");
        assertTrue(hash.startsWith("pbkdf2-sha256$600000$"));
        assertTrue(hasher.verificar("UnaPasswordSegura-2026", hash));
        assertFalse(hasher.verificar("incorrecta", hash));
        assertFalse(hasher.requiereActualizacion(hash));
    }

    @Test
    void rechazaHashesMalformadosSinLanzarExcepcion() {
        assertFalse(hasher.verificar("password", "no-es-un-hash"));
        assertFalse(hasher.verificar("password", "pbkdf2-sha256$99999999$AA==$AA=="));
        assertTrue(hasher.requiereActualizacion("no-es-un-hash"));
    }

    @Test
    void conservaCompatibilidadConHashAnteriorYLoMarcaParaMigrar() throws Exception {
        byte[] salt = new byte[16];
        PBEKeySpec spec = new PBEKeySpec("PasswordAnterior-2026".toCharArray(), salt, 65_536, 128);
        byte[] derivada = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String legacy = Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derivada);
        assertTrue(hasher.verificar("PasswordAnterior-2026", legacy));
        assertTrue(hasher.requiereActualizacion(legacy));
    }

    @Test
    void hashVersionadoDe310MilIteracionesSeVerificaYSeMigra() throws Exception {
        byte[] salt = new byte[16];
        PBEKeySpec spec = new PBEKeySpec("PasswordAnterior-2026".toCharArray(), salt, 310_000, 256);
        byte[] derivada = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String anterior = "pbkdf2-sha256$310000$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derivada);
        assertTrue(hasher.verificar("PasswordAnterior-2026", anterior));
        assertTrue(hasher.requiereActualizacion(anterior));
    }
}
