package com.stayhub.servicioDeOverbooking.dto;

import java.time.LocalDate;

public record ConflictoReservaDTO(
        Long reservaIdConflictiva,
        Long hotelId,
        String tipoHabitacion,
        int cantidadHabitaciones,
        LocalDate checkIn,
        LocalDate checkOut,
        String canalOrigen,
        String referenciaExterna,
        String huespedEmail) { }