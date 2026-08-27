package com.stayhub.usuarios.exception;

public class UsuarioException extends RuntimeException {
    private final CodigoErrorUsuario codigo;

    public UsuarioException(CodigoErrorUsuario codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CodigoErrorUsuario getCodigo() { return codigo; }
}