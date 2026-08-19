package com.stayhub.reservas.repository;

import com.stayhub.reservas.model.Reserva;
import java.util.List;
import java.util.Optional;

/**
 * Patrón DAO: aísla el acceso a datos del resto de la lógica de negocio.
 * ServicioDeReservasImpl depende de esta interfaz, no de JPA directamente.
 */
public interface ReservaRepository {

    Reserva guardar(Reserva reserva);

    Optional<Reserva> buscarPorId(Long id);

    Optional<Reserva> buscarPorCanalYReferencia(String canal, String referenciaExterna);

    List<Reserva> listarPorHotel(Long hotelId);
}
