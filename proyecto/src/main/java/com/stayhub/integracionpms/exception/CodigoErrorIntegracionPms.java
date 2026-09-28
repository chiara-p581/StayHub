package com.stayhub.integracionpms.exception;

import com.stayhub.inventarioytarifas.exception.CodigoErrorInventarioTarifas;
import com.stayhub.reservas.exception.CodigoErrorReserva;

/**
 * Códigos de error que el PMS recibe dentro del SOAP Fault (elemento
 * detail/errorIntegracionPms/codigo). Unifica los códigos de los componentes
 * internos para que el sistema legado no dependa de ellos.
 */
public enum CodigoErrorIntegracionPms {
    SOLICITUD_INVALIDA,
    RESERVA_NO_ENCONTRADA,
    TRANSICION_DE_ESTADO_INVALIDA,
    SIN_DISPONIBILIDAD,
    DEPENDENCIA_NO_DISPONIBLE,
    ERROR_INTERNO;

    public static CodigoErrorIntegracionPms desde(CodigoErrorReserva codigo) {
        if (codigo == null) return ERROR_INTERNO;
        return switch (codigo) {
            case SOLICITUD_INVALIDA, RESERVA_DUPLICADA, NO_AUTORIZADO -> SOLICITUD_INVALIDA;
            case RESERVA_NO_ENCONTRADA -> RESERVA_NO_ENCONTRADA;
            case TRANSICION_DE_ESTADO_INVALIDA -> TRANSICION_DE_ESTADO_INVALIDA;
            case SIN_DISPONIBILIDAD -> SIN_DISPONIBILIDAD;
            case DEPENDENCIA_NO_DISPONIBLE -> DEPENDENCIA_NO_DISPONIBLE;
        };
    }

    public static CodigoErrorIntegracionPms desde(CodigoErrorInventarioTarifas codigo) {
        if (codigo == null) return ERROR_INTERNO;
        return switch (codigo) {
            case SOLICITUD_INVALIDA -> SOLICITUD_INVALIDA;
            case TRANSICION_DE_ESTADO_INVALIDA -> TRANSICION_DE_ESTADO_INVALIDA;
            case HOLD_NO_ENCONTRADO, SOBREVENTA_DETECTADA -> ERROR_INTERNO;
        };
    }
}
