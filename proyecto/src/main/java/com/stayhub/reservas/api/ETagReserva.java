package com.stayhub.reservas.api;

import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.service.ServicioDeReservas;

/**
 * Traducción entre la versión de una reserva (@Version de JPA) y los headers
 * HTTP de control de concurrencia optimista:
 *
 * <ul>
 *   <li>{@code ETag: "3"} se devuelve al consultar o modificar una reserva.</li>
 *   <li>{@code If-Match: "3"} lo envía el cliente al modificarla. Si otra
 *       persona la cambió mientras tanto, la versión ya no coincide y la API
 *       responde 412 Precondition Failed sin pisar el dato más reciente.</li>
 * </ul>
 *
 * Se mantiene sin dependencias del runtime JAX-RS para poder testearla con
 * JUnit puro.
 */
public final class ETagReserva {

    private ETagReserva() { }

    /** Valor del header ETag para una versión: strong ETag, entre comillas (RFC 9110). */
    public static String formatear(Long version) {
        return "\"" + (version == null ? 0L : version) + "\"";
    }

    /**
     * Interpreta el header If-Match.
     *
     * @return la versión pedida; {@link ServicioDeReservas#CUALQUIER_VERSION}
     *         para "*"; {@code null} si el header no vino.
     */
    public static Long versionDesdeIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String valor = ifMatch.trim();
        if (valor.equals("*")) {
            return ServicioDeReservas.CUALQUIER_VERSION;
        }
        if (valor.contains(",")) {
            throw invalido(ifMatch);
        }
        if (valor.startsWith("W/")) {
            valor = valor.substring(2);
        }
        if (valor.length() >= 2 && valor.startsWith("\"") && valor.endsWith("\"")) {
            valor = valor.substring(1, valor.length() - 1);
        }
        try {
            long version = Long.parseLong(valor);
            if (version < 0) {
                throw invalido(ifMatch);
            }
            return version;
        } catch (NumberFormatException ex) {
            throw invalido(ifMatch);
        }
    }

    private static ReservaException invalido(String ifMatch) {
        return new ReservaException(CodigoErrorReserva.SOLICITUD_INVALIDA,
                "If-Match inválido: se esperaba el ETag de la reserva (por ejemplo \"3\"), se recibió " + ifMatch);
    }
}
