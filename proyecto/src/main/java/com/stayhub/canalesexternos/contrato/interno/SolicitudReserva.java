package com.stayhub.canalesexternos.contrato.interno;

import com.stayhub.canalesexternos.dto.HuespedDTO;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SolicitudReserva(
        String referenciaExterna, String canal, Long hotelId, LocalDate checkIn,
        LocalDate checkOut, String tipoHabitacion, int cantidadHabitaciones,
        HuespedDTO huesped, BigDecimal precioTotal, String moneda) { }
