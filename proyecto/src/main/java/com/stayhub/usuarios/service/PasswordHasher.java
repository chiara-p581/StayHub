package com.stayhub.usuarios.service;

import jakarta.enterprise.context.ApplicationScoped;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashea contraseñas con PBKDF2 (viene incluido en el JDK, sin dependencias
 * extra). Los hashes nuevos incluyen algoritmo e iteraciones para poder
 * aumentar el costo sin invalidar las cuentas existentes.
 */
@ApplicationScoped
public class PasswordHasher {

    static final int ITERACIONES = 310_000;
    private static final int ITERACIONES_LEGACY = 65_536;
    private static final int LONGITUD_CLAVE = 256;
    private static final int LONGITUD_CLAVE_LEGACY = 128;
    private static final String VERSION = "pbkdf2-sha256";
    private final SecureRandom random = new SecureRandom();

    public String hash(String passwordPlano) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(passwordPlano, salt, ITERACIONES, LONGITUD_CLAVE);
        return VERSION + "$" + ITERACIONES + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public boolean verificar(String passwordPlano, String hashGuardado) {
        if (passwordPlano == null || hashGuardado == null) return false;
        try {
            if (hashGuardado.startsWith(VERSION + "$")) {
                String[] partes = hashGuardado.split("\\$", -1);
                if (partes.length != 4) return false;
                int iteraciones = Integer.parseInt(partes[1]);
                if (iteraciones < ITERACIONES_LEGACY || iteraciones > 2_000_000) return false;
                byte[] salt = Base64.getDecoder().decode(partes[2]);
                byte[] esperado = Base64.getDecoder().decode(partes[3]);
                if (salt.length < 16 || esperado.length < 16) return false;
                return java.security.MessageDigest.isEqual(esperado,
                        pbkdf2(passwordPlano, salt, iteraciones, esperado.length * 8));
            }
            String[] partes = hashGuardado.split(":", -1);
            if (partes.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(partes[0]);
            byte[] esperado = Base64.getDecoder().decode(partes[1]);
            if (salt.length != 16 || esperado.length != LONGITUD_CLAVE_LEGACY / 8) return false;
            return java.security.MessageDigest.isEqual(esperado,
                    pbkdf2(passwordPlano, salt, ITERACIONES_LEGACY, LONGITUD_CLAVE_LEGACY));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean requiereActualizacion(String hashGuardado) {
        if (hashGuardado == null || !hashGuardado.startsWith(VERSION + "$")) return true;
        try {
            String[] partes = hashGuardado.split("\\$", -1);
            return partes.length != 4 || Integer.parseInt(partes[1]) < ITERACIONES;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iteraciones, int bits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iteraciones, bits);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error hasheando password", e);
        }
    }
}
