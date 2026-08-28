package com.stayhub.notificaciones.exception;

public class NotificacionException extends RuntimeException {
    private final CodigoErrorNotificacion codigo;

    public NotificacionException(CodigoErrorNotificacion codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public NotificacionException(CodigoErrorNotificacion codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public CodigoErrorNotificacion getCodigo() { return codigo; }
}