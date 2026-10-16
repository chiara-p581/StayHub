package com.stayhub.seguridad;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class SeguridadException extends RuntimeException {
    private final int estado;
    public SeguridadException(int estado, String mensaje) { super(mensaje); this.estado = estado; }
    public int getEstado() { return estado; }
}
