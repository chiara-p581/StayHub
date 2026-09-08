package com.stayhub.servicioDeOverbooking.contrato;

import com.stayhub.servicioDeOverbooking.dto.ConflictoReservaDTO;
import com.stayhub.servicioDeOverbooking.dto.ResultadoResolucionOverbookingDTO;

public interface ServicioDeOverbooking {
    ResultadoResolucionOverbookingDTO resolverConflicto(ConflictoReservaDTO conflicto);
}