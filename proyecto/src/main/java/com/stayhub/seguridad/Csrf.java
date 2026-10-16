package com.stayhub.seguridad;

import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Csrf {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Csrf() {}
    public static String token(HttpSession sesion) {
        synchronized (sesion) {
            String token = (String) sesion.getAttribute("csrfToken");
            if (token == null) {
                byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                sesion.setAttribute("csrfToken", token);
            }
            return token;
        }
    }
    public static boolean valido(HttpSession sesion, String recibido) {
        if (sesion == null || recibido == null || recibido.length() > 100) return false;
        String esperado = (String) sesion.getAttribute("csrfToken");
        return esperado != null && MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }
}
