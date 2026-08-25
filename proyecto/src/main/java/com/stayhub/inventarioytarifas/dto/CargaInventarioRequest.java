package com.stayhub.inventarioytarifas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entrada para dar de alta o actualizar capacidad y tarifa de un tipo de
 * habitación de un hotel, día por día, en el rango [desde, hasta).
 */
public record CargaInventarioRequest(
        Long hotelId,
        String tipoHabitacion,
        LocalDate desde,
        LocalDate hasta,
        int unidadesTotales,
        BigDecimal precio,
        String moneda) { }
