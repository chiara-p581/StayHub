package com.stayhub.canalesexternos.dto;

import java.math.BigDecimal;

/** Oferta de catálogo simulada para probar búsquedas multicanal sin depender de credenciales comerciales. */
public record OfertaCatalogoDTO(Long hotelId, String canal, BigDecimal precioDesde,
                                String moneda, double puntuacion, int opiniones) { }
