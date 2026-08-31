package com.stayhub.inventarioytarifas.repository;

import com.stayhub.inventarioytarifas.model.InventarioDiario;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventarioDiarioRepository {

    InventarioDiario guardar(InventarioDiario inventario);

    /** Lectura sin bloqueo, para consultas de solo lectura (disponibilidad/tarifas). */
    Optional<InventarioDiario> buscarPorHotelTipoYFecha(Long hotelId, String tipoHabitacion, LocalDate fecha);

    /**
     * Igual que buscarPorHotelTipoYFecha pero tomando un bloqueo pesimista de
     * escritura sobre la fila (SELECT ... FOR UPDATE en PostgreSQL).
     *
     * Es lo que usan crearHold/reemplazarHold/liberarHold/cargarInventario para
     * cerrar la ventana "leer cupo -> decidir -> escribir": sin el bloqueo, dos
     * transacciones podían leer el mismo cupo libre y ambas darlo por bueno, y
     * la perdedora terminaba en OptimisticLockException (500 intermitente) en
     * vez de en una respuesta de falta de disponibilidad. Con el bloqueo, la
     * segunda transacción espera y vuelve a leer el cupo ya descontado.
     *
     * Requiere una transacción activa: llamar solo desde métodos transaccionales.
     */
    Optional<InventarioDiario> buscarPorHotelTipoYFechaBloqueando(Long hotelId, String tipoHabitacion, LocalDate fecha);

    /** Todas las filas de todos los tipos de habitación de un hotel dentro de [desde, hasta). */
    List<InventarioDiario> buscarPorHotelYRango(Long hotelId, LocalDate desde, LocalDate hasta);
}
