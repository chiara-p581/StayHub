package com.stayhub.inventarioytarifas.model;

/**
 * PENDIENTE  -> se retuvo el cupo (crearHold) pero todavía no se confirmó.
 * CONFIRMADO -> ServicioDeReservas confirmó la reserva asociada (confirmarHold).
 * LIBERADO   -> el cupo retenido se devolvió al inventario (liberarHold), ya
 *               sea porque se canceló, se rechazó o se reemplazó por otro hold.
 */
public enum EstadoHold {
    PENDIENTE,
    CONFIRMADO,
    LIBERADO
}
