package com.stayhub.servicioDeOverbooking.dto;

import com.stayhub.servicioDeOverbooking.model.EstrategiaResolucion;
import java.time.OffsetDateTime;

public record ResultadoResolucionOverbookingDTO(
        Long conflictoId,
        Long reservaIdConflictiva,
        boolean resuelto,
        EstrategiaResolucion estrategia,
        Long reservaAlternativaId,
        String mensaje,
        OffsetDateTime fecha) { }