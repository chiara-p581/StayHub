package com.stayhub.reservas.contrato;

/** Lanzada por GestionDeDisponibilidadPort.crearHold cuando no hay cupo. */
public class SinDisponibilidadException extends RuntimeException {
    public SinDisponibilidadException(String mensaje) {
        super(mensaje);
    }
}
