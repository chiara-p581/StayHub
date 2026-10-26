package com.stayhub.reservas.service;

import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.dto.ReservaResponse;
import java.util.List;

/**
 * Operaciones "internas": las usa StayHub cuando un usuario o administrador
 * reserva directamente en la plataforma (no a través de una OTA).
 *
 * Estas operaciones comparten toda la lógica de negocio con las que llegan
 * por ServicioDeReservasPort (ver ServicioDeReservasImpl) — lo único que
 * cambia es el origen del pedido y el formato de entrada/salida.
 */
public interface ServicioDeReservas {

    ReservaResponse crearReserva(ReservaRequest solicitud);

    ReservaResponse consultarReserva(Long id);

    ReservaResponse confirmarReserva(Long id);
    ReservaResponse consultarParaPago(Long id);

    /** Mutaciones autorizadas contra el usuario de la sesión y el propietario persistido. */
    ReservaResponse modificarReserva(Long id, ReservaRequest solicitud);

    ReservaResponse cancelarReserva(Long id);

    List<ReservaResponse> listarPorHotel(Long hotelId);

    List<ReservaResponse> listarPorHuespedEmail(String email);
    List<ReservaResponse> listarMias(int offset, int limite);
    List<ReservaResponse> listarPorHotel(Long hotelId, int offset, int limite);
    List<ReservaResponse> listarLlegadas(Long hotelId, java.time.LocalDate fecha);
}
