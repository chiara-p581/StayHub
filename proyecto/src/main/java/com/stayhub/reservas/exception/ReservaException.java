package com.stayhub.reservas.exception;

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
