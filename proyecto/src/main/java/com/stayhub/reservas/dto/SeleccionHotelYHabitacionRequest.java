package com.stayhub.reservas.dto;

/** Primer paso del carrito: qué hotel, tipo de habitación y cuántas. */
public record SeleccionHotelYHabitacionRequest(
        Long hotelId,
        String tipoHabitacion,
        int cantidadHabitaciones) { }
