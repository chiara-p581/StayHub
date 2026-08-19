package com.stayhub.reservas.contrato;

import java.time.LocalDate;

/**
 * PROPUESTA de contrato hacia ServicioDeInventarioYTarifas, a coordinar con
 * quien implemente ese componente.
 *
 * El puerto que ya existe (ServicioDeInventarioYTarifasPort, definido por
 * Chiara en com.stayhub.canalesexternos.contrato.interno) solo sirve para
 * CONSULTAR disponibilidad/tarifas de solo lectura, pensado para publicar
 * en OTAs — no alcanza para lo que necesita ServicioDeReservas, que además
 * de consultar necesita "reservar" (hold) el cupo mientras la reserva está
 * pendiente, y confirmarlo o liberarlo según el resultado.
 *
 * Se sigue el mismo patrón que usó Chiara: interfaz simple, implementada
 * como bean CDI/EJB por InventarioYTarifas, inyectada acá vía
 * Instance<GestionDeDisponibilidadPort> con isResolvable() para no romper
 * el despliegue si ese componente todavía no existe.
 */
public interface GestionDeDisponibilidadPort {

    /**
     * Intenta retener el cupo. Devuelve el id del hold si hay disponibilidad,
     * o lanza SinDisponibilidadException si no la hay.
     */
    String crearHold(Long hotelId, String tipoHabitacion, int cantidadHabitaciones,
                      LocalDate checkIn, LocalDate checkOut);

    /** Convierte el hold en disponibilidad definitiva. */
    void confirmarHold(String holdId);

    /** Libera el cupo retenido (reserva rechazada, cancelada o expirada). */
    void liberarHold(String holdId);
}
