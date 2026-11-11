package com.stayhub.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class CodigosRecuperacionMfa {
    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigosRecuperacionMfa() { }

    static List<String> generar() {
        List<String> codigos = new ArrayList<>();
        while (codigos.size() < 8) {
            StringBuilder codigo = new StringBuilder(14);
            for (int i = 0; i < 12; i++) {
                if (i == 4 || i == 8) codigo.append('-');
                codigo.append(ALFABETO[RANDOM.nextInt(ALFABETO.length)]);
            }
            String valor = codigo.toString();
            if (!codigos.contains(valor)) codigos.add(valor);
        }
        return List.copyOf(codigos);
    }

    static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    static boolean tieneFormato(String codigo) {
        return normalizar(codigo).matches("[A-HJ-NP-Z2-9]{4}(?:-[A-HJ-NP-Z2-9]{4}){2}");
    }

    static String hash(String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizar(codigo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo proteger el código de recuperación", ex);
        }
    }
}
