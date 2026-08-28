package com.stayhub.servicioDeOverbooking.dto;

import java.time.LocalDate;

public record ConflictoReservaDTO(
        Long reservaIdConflictiva,
        Long hotelId,
        String tipoHabitacion,
        LocalDate checkIn,
        LocalDate checkOut,
        String canalOrigen,
        String referenciaExterna) { }