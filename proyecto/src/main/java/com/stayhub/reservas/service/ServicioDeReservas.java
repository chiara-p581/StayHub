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

    /**
     * actorEmail / actorEsAdmin: identidad de quien hace el pedido, resuelta
     * por ReservaResource a partir de la sesión HTTP (el login nuevo,
     * basado en AutenticacionFilter, ya no pasa por el contenedor de EJBs,
     * así que no podemos usar SessionContext acá dentro). Un ADMIN puede
     * modificar/cancelar cualquier reserva; un HUESPED solo la propia.
     */
    ReservaResponse modificarReserva(Long id, ReservaRequest solicitud, String actorEmail, boolean actorEsAdmin);

    ReservaResponse cancelarReserva(Long id, String actorEmail, boolean actorEsAdmin);

    List<ReservaResponse> listarPorHotel(Long hotelId);
}
