package com.stayhub.seguridad;

/** Reglas compartidas para el header HTTP Idempotency-Key. */
public final class ClaveIdempotencia {
    private ClaveIdempotencia() { }

    public static String normalizar(String clave) {
        if (clave == null || clave.isBlank()) return null;
        String valor = clave.trim();
        if (!valor.matches("[A-Za-z0-9._:-]{16,80}")) {
            throw new SeguridadException(400,
                    "Idempotency-Key debe tener entre 16 y 80 caracteres seguros");
        }
        return valor;
    }

    public static String requerida(String clave) {
        String valor = normalizar(clave);
        if (valor == null) {
            throw new SeguridadException(400,
                    "Falta el header Idempotency-Key para esta operación");
        }
        return valor;
    }

    /**
     * Permite una transición compatible desde el campo JSON histórico, pero
     * rechaza que header y cuerpo identifiquen operaciones distintas.
     */
    public static String combinar(String header, String cuerpo) {
        String h = normalizar(header);
        String c = normalizar(cuerpo);
        if (h != null && c != null && !h.equals(c)) {
            throw new SeguridadException(400,
                    "Idempotency-Key no coincide con la clave del cuerpo");
        }
        return h != null ? h : c;
    }
}
