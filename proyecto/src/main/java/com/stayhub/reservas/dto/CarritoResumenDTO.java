package com.stayhub.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Estado actual del carrito de reserva de la sesión (lo que se fue
 * acumulando en CarritoDeReserva a través de varias llamadas HTTP
 * independientes). listoParaConfirmar indica si ya están todos los datos
 * necesarios para llamar a /carrito/confirmar.
 */
public record CarritoResumenDTO(
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
        String moneda,
        boolean listoParaConfirmar) { }
