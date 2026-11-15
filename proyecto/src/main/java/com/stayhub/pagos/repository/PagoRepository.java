package com.stayhub.pagos.repository;

import com.stayhub.pagos.model.Pago;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

public interface PagoRepository {

    Pago guardar(Pago pago);

    Optional<Pago> buscarPorId(Long id);

    Optional<Pago> buscarPorClaveIdempotencia(Long usuarioId, String clave);

    void bloquearUsuario(Long usuarioId);

    void bloquearReservas(List<Long> reservaIds);

    boolean existeAprobadoParaReserva(Long reservaId);

    List<Pago> listarPorReservas(List<Long> reservaIds);
    List<Pago> listarPorUsuario(Long id, int offset, int limite);
    List<Pago> buscarAdmin(Long id, String usuario, Long hotelId, BigDecimal montoMin,
            BigDecimal montoMax, String estado, int offset, int limite);
    long contarAdmin(Long id, String usuario, Long hotelId, BigDecimal montoMin,
            BigDecimal montoMax, String estado);
}
