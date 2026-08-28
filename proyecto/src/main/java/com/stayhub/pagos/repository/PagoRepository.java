package com.stayhub.pagos.repository;

import com.stayhub.pagos.model.Pago;
import java.util.Optional;

public interface PagoRepository {

    Pago guardar(Pago pago);

    Optional<Pago> buscarPorId(Long id);
}