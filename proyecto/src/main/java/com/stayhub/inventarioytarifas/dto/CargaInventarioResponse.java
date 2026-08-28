package com.stayhub.inventarioytarifas.dto;

import java.time.LocalDate;

public record CargaInventarioResponse(
        Long hotelId,
        String tipoHabitacion,
        LocalDate desde,
        LocalDate hasta,
        int diasCargados) { }
