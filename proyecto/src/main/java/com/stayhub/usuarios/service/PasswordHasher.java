package com.stayhub.usuarios.service;

import jakarta.enterprise.context.ApplicationScoped;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashea contraseñas con PBKDF2 (viene incluido en el JDK, sin dependencias
 * extra). El resultado guardado tiene el formato "salt:hash", ambos en
 * Base64, para no necesitar una columna aparte para la salt.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final int ITERACIONES = 65536;
    private static final int LONGITUD_CLAVE = 128;

    public String hash(String passwordPlano) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(passwordPlano, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public boolean verificar(String passwordPlano, String hashGuardado) {
        String[] partes = hashGuardado.split(":");
        byte[] salt = Base64.getDecoder().decode(partes[0]);
        byte[] hashEsperado = Base64.getDecoder().decode(partes[1]);
        byte[] hashCalculado = pbkdf2(passwordPlano, salt);
        return java.security.MessageDigest.isEqual(hashEsperado, hashCalculado);
    }

    private byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERACIONES, LONGITUD_CLAVE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Error hasheando password", e);
        }
    }
}