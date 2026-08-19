package com.stayhub.reservas.model;

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
    RECHAZADA
}
