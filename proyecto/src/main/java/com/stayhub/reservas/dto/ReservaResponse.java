package com.stayhub.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Salida para las operaciones "internas" (REST propio de ServicioDeReservas). */
public record ReservaResponse(
        Long id,
        String canal,
        String referenciaExterna,
        Long hotelId,
        String tipoHabitacion,
        int cantidadHabitaciones,
        LocalDate checkIn,
        LocalDate checkOut,
        String huespedNombre,
        String huespedApellido,
        String huespedEmail,
        BigDecimal precioTotal,
        String moneda,
        String estado,
        LocalDateTime fechaCreacion) { }
