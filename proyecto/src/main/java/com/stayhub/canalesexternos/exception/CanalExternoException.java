package com.stayhub.canalesexternos.exception;

public class CanalExternoException extends RuntimeException {
    private final CodigoErrorCanal codigo;

    public CanalExternoException(CodigoErrorCanal codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CanalExternoException(CodigoErrorCanal codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public CodigoErrorCanal getCodigo() { return codigo; }
}
