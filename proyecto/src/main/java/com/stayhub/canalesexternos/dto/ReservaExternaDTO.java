package com.stayhub.canalesexternos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReservaExternaDTO(
        String idExterno,
        Canal canal,
        Long hotelId,
        LocalDate checkIn,
        LocalDate checkOut,
        String tipoHabitacion,
        int cantidadHabitaciones,
        HuespedDTO huesped,
        BigDecimal precioTotal,
        String moneda,
        EstadoReservaExterna estado) { }
