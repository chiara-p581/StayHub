package com.stayhub.inventarioytarifas.dto;

import java.time.LocalDate;

public record DisponibilidadDTO(
        Long hotelId,
        String tipoHabitacion,
        LocalDate desde,
        LocalDate hasta,
        int unidadesDisponibles) { }
