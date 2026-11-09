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

    /**
     * Sincroniza los cambios pendientes con la base para que JPA incremente
     * @Version antes de armar la respuesta (y el ETag devuelto sea el nuevo).
     */
    default void sincronizar() { }

    Optional<Reserva> buscarPorId(Long id);
    Optional<Reserva> buscarPorIdBloqueando(Long id);

    Optional<Reserva> buscarPorCanalYReferencia(String canal, String referenciaExterna);

    Optional<Reserva> buscarPorUsuarioYClaveIdempotencia(Long usuarioId, String clave);

    List<Reserva> listarPorHotel(Long hotelId);

    List<Reserva> listarPorHuespedEmail(String email);

    List<Reserva> listarPorUsuario(Long id, int offset, int limite);
    long contarPendientesPorUsuario(Long id);
    void bloquearUsuario(Long id);
    List<Reserva> pendientesVencidas(java.time.LocalDateTime antes);
    List<Reserva> listarLlegadas(Long hotelId, java.time.LocalDate fecha);
    List<Reserva> listarPorHotel(Long hotelId, int offset, int limite);
}
