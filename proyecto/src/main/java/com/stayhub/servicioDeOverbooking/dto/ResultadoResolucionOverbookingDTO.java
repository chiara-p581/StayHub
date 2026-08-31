package com.stayhub.servicioDeOverbooking.dto;

import com.stayhub.servicioDeOverbooking.model.EstrategiaResolucion;
import java.time.OffsetDateTime;

public record ResultadoResolucionOverbookingDTO(
        Long conflictoId,
        Long reservaIdConflictiva,
        boolean resuelto,
        EstrategiaResolucion estrategia,
        String tipoHabitacionAlternativa,
        String holdAlternativoId,
        String mensaje,
        OffsetDateTime fecha) { }