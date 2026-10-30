package com.stayhub.reservas.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de una reserva dentro de StayHub.
 *
 * PENDIENTE   -> se creó la reserva y se solicitó el hold de disponibilidad a
 *                ServicioDeInventarioYTarifas, pero todavía no se confirmó.
 * CONFIRMADA  -> el hold se convirtió en disponibilidad definitiva (y el pago,
 *                si corresponde, fue autorizado).
 * MODIFICADA  -> se actualizaron fechas/habitaciones de una reserva ya
 *                confirmada; vuelve a pasar por control de disponibilidad.
 * CANCELADA   -> la reserva fue cancelada (por el cliente, el hotel, o por
 *                falta de confirmación a tiempo).
 * RECHAZADA   -> no pudo confirmarse (sin disponibilidad, datos inválidos, o
 *                conflicto resuelto en contra por ServicioDeOverbooking).
 */
public enum EstadoReserva {
    PENDIENTE,
    CONFIRMADA,
    MODIFICADA,
    CANCELADA,
    RECHAZADA;

    /**
     * Tabla única del ciclo de vida. Las repeticiones idempotentes se resuelven
     * en el servicio y no se consideran una transición de estado.
     */
    public boolean permiteTransicionA(EstadoReserva destino) {
        if (destino == null || destino == this) return false;
        return transicionesPermitidas().contains(destino);
    }

    public Set<EstadoReserva> transicionesPermitidas() {
        return switch (this) {
            case PENDIENTE -> EnumSet.of(CONFIRMADA, MODIFICADA, CANCELADA, RECHAZADA);
            case CONFIRMADA -> EnumSet.of(MODIFICADA, CANCELADA);
            case MODIFICADA -> EnumSet.of(PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA);
            case RECHAZADA -> EnumSet.of(MODIFICADA, CANCELADA);
            case CANCELADA -> EnumSet.noneOf(EstadoReserva.class);
        };
    }
}
