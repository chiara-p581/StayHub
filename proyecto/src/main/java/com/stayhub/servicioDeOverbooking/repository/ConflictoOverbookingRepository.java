package com.stayhub.servicioDeOverbooking.repository;

import com.stayhub.servicioDeOverbooking.model.ConflictoOverbooking;
import java.util.Optional;

public interface ConflictoOverbookingRepository {
    ConflictoOverbooking guardar(ConflictoOverbooking conflicto);
    Optional<ConflictoOverbooking> buscarPorId(Long id);
}