package com.stayhub.reservas.exception;

public enum CodigoErrorReserva {
    SOLICITUD_INVALIDA,
    RESERVA_NO_ENCONTRADA,
    RESERVA_DUPLICADA,
    SIN_DISPONIBILIDAD,
    DEPENDENCIA_NO_DISPONIBLE,
    TRANSICION_DE_ESTADO_INVALIDA,
    NO_AUTORIZADO,
    /** El If-Match no coincide con la versión actual: alguien la modificó antes (HTTP 412). */
    VERSION_OBSOLETA,
    /** Falta el header If-Match en una actualización que puede competir (HTTP 428). */
    VERSION_REQUERIDA
}
