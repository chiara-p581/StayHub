package com.stayhub.usuarios.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

/** Límites de iteraciones y longitudes mínimas de salt/hash en PasswordHasher.verificar. */
class PasswordHasherMutantesTest {
    private final PasswordHasher hasher = new PasswordHasher();
    private static final String PASSWORD = "PasswordLimite-2026";

    private String hash(int iteraciones, int bytesSalt, int bytesHash) throws Exception {
        byte[] salt = new byte[bytesSalt];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) (i + 1);
        PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray(), salt, iteraciones, bytesHash * 8);
        byte[] derivada = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return "pbkdf2-sha256$" + iteraciones + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derivada);
    }

    @Test void iteracionesIgualesAlMinimoSeAceptan() throws Exception {
        String h = hash(65_536, 16, 32);
        assertTrue(hasher.verificar(PASSWORD, h));
        assertFalse(hasher.verificar("otra", h));
    }

    @Test void iteracionesPorDebajoDelMinimoSeRechazanAunqueElHashSeaCorrecto() throws Exception {
        assertFalse(hasher.verificar(PASSWORD, hash(65_535, 16, 32)));
    }

    @Test void iteracionesIgualesAlMaximoSeAceptanYPorEncimaSeRechazan() throws Exception {
        assertTrue(hasher.verificar(PASSWORD, hash(2_000_000, 16, 16)));
        assertFalse(hasher.verificar(PASSWORD, hash(2_000_001, 16, 16)));
    }

    @Test void saltDeExactamente16BytesSeAcepta() throws Exception {
        assertTrue(hasher.verificar(PASSWORD, hash(65_536, 16, 32)));
    }

    @Test void saltDe15BytesSeRechazaAunqueElHashSeaCorrecto() throws Exception {
        assertFalse(hasher.verificar(PASSWORD, hash(65_536, 15, 32)));
    }

    @Test void hashEsperadoDeExactamente16BytesSeAcepta() throws Exception {
        assertTrue(hasher.verificar(PASSWORD, hash(65_536, 16, 16)));
    }

    @Test void hashEsperadoDe15BytesSeRechazaAunqueSeaCorrecto() throws Exception {
        assertFalse(hasher.verificar(PASSWORD, hash(65_536, 16, 15)));
    }
}
