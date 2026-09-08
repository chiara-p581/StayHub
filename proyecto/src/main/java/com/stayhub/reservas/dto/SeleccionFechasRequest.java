package com.stayhub.reservas.dto;

import java.time.LocalDate;

/** Segundo paso del carrito: check-in y check-out elegidos. */
public record SeleccionFechasRequest(
        LocalDate checkIn,
        LocalDate checkOut) { }
