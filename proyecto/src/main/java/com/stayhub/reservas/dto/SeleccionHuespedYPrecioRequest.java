package com.stayhub.reservas.dto;

import java.math.BigDecimal;

/** Tercer paso del carrito: datos del huésped y precio acordado. */
public record SeleccionHuespedYPrecioRequest(
        String huespedNombre,
        String huespedApellido,
        String huespedEmail,
        String huespedTelefono,
        BigDecimal precioTotal,
        String moneda) { }
