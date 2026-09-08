package com.stayhub.reservas.exception;

import jakarta.ejb.ApplicationException;

/**
 * Error de negocio de ServicioDeReservas, traducido a HTTP por
 * ReservaExceptionMapper según su CodigoErrorReserva.
 *
 * @ApplicationException por el mismo motivo que ya está documentado en
 * SinDisponibilidadException: esta excepción sale de un EJB
 * (ServicioDeReservasImpl) hacia quien lo llama, y sin la anotación el
 * contenedor la considera una falla grave del sistema y la envuelve en
 * EJBException / EJBTransactionRolledbackException. Envuelta, deja de ser
 * un ReservaException para JAX-RS y ReservaExceptionMapper no la ve, así que
 * un RESERVA_NO_ENCONTRADA salía como 500 en vez de 404 y un
 * SIN_DISPONIBILIDAD como 500 en vez de 409.
 *
 * rollback = true (al revés que SinDisponibilidadException, que se atrapa y
 * necesita seguir usando la misma transacción): acá nadie la atrapa, siempre
 * significa "esta operación no se hace", y revertir garantiza que no quede
 * persistido nada a medio aplicar.
 */
@ApplicationException(rollback = true)
public class ReservaException extends RuntimeException {
    private final CodigoErrorReserva codigo;

    public ReservaException(CodigoErrorReserva codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public ReservaException(CodigoErrorReserva codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public CodigoErrorReserva getCodigo() { return codigo; }
}
