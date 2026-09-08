package com.stayhub.pagos.exception;

public class PagoException extends RuntimeException {
    private final CodigoErrorPago codigo;

    public PagoException(CodigoErrorPago codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public PagoException(CodigoErrorPago codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public CodigoErrorPago getCodigo() { return codigo; }
}