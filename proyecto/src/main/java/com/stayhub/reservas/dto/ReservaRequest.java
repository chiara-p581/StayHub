package com.stayhub.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entrada para una reserva creada DIRECTAMENTE en StayHub (no viene de una
 * OTA). No trae canal ni referenciaExterna: el servicio les asigna
 * canal = "DIRECTA" y genera su propia referencia.
 */
public record ReservaRequest(
        Long hotelId,
        String tipoHabitacion,
        int cantidadHabitaciones,
        LocalDate checkIn,
        LocalDate checkOut,
        String huespedNombre,
        String huespedApellido,
        String huespedEmail,
        String huespedTelefono,
        BigDecimal precioTotal,
        String moneda) { }
