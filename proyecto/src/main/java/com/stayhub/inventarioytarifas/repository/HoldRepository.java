package com.stayhub.inventarioytarifas.repository;

import com.stayhub.inventarioytarifas.model.Hold;
import java.util.Optional;

public interface HoldRepository {
    Hold guardar(Hold hold);
    Optional<Hold> buscarPorId(String id);
}
