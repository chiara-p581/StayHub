package com.stayhub.servicioDeOverbooking.exception;

public class OverbookingException extends RuntimeException {
    private final CodigoErrorOverbooking codigo;

    public OverbookingException(CodigoErrorOverbooking codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public OverbookingException(CodigoErrorOverbooking codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public CodigoErrorOverbooking getCodigo() { return codigo; }
}