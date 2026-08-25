package com.stayhub.inventarioytarifas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TarifaDTO(
        Long hotelId,
        String tipoHabitacion,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal importe,
        String moneda) { }
