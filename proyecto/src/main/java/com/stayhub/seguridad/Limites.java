package com.stayhub.seguridad;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Límites de negocio; también se aplican a llamadas internas y mensajes JMS. */
public final class Limites {
    public static final int RESULTADOS = 100;
    public static final int NOCHES = 366;
    public static final int HABITACIONES = 10;
    public static final int LOTE = 20;
    private Limites() {}
    public static void rango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)
                || ChronoUnit.DAYS.between(desde, hasta) > NOCHES
                || hasta.isAfter(LocalDate.now().plusYears(2)))
            throw new SeguridadException(400, "El período debe contener entre 1 y 366 noches, dentro de los próximos dos años");
    }
    public static void texto(String texto, int maximo, boolean requerido) {
        if ((requerido && (texto == null || texto.isBlank())) || (texto != null && texto.length() > maximo))
            throw new SeguridadException(400, "Un campo está vacío o excede la longitud permitida");
    }
    public static void email(String email) {
        texto(email, 120, true);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new SeguridadException(400, "El email no es válido");
    }
    public static void cantidad(int cantidad) {
        if (cantidad < 1 || cantidad > HABITACIONES)
            throw new SeguridadException(400, "Se permiten entre 1 y 10 habitaciones por reserva");
    }
    public static int pagina(int offset, int limite) {
        if (offset < 0 || offset > 10000 || limite < 1 || limite > RESULTADOS)
            throw new SeguridadException(400, "Paginación inválida: offset 0..10000 y limite 1..100");
        return limite;
    }
}
