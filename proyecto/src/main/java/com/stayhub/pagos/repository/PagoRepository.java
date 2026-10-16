package com.stayhub.pagos.repository;

import com.stayhub.pagos.model.Pago;
import java.util.Optional;
import java.util.List;

public interface PagoRepository {

    Pago guardar(Pago pago);

    Optional<Pago> buscarPorId(Long id);

    Optional<Pago> buscarPorClaveIdempotencia(String clave);

    void bloquearReservas(List<Long> reservaIds);

    boolean existeAprobadoParaReserva(Long reservaId);

    List<Pago> listarPorReservas(List<Long> reservaIds);
    List<Pago> listarPorUsuario(Long id, int offset, int limite);
}
