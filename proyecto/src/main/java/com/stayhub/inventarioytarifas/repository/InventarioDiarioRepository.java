package com.stayhub.inventarioytarifas.repository;

import com.stayhub.inventarioytarifas.model.InventarioDiario;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventarioDiarioRepository {

    InventarioDiario guardar(InventarioDiario inventario);

    Optional<InventarioDiario> buscarPorHotelTipoYFecha(Long hotelId, String tipoHabitacion, LocalDate fecha);

    /** Todas las filas de todos los tipos de habitación de un hotel dentro de [desde, hasta). */
    List<InventarioDiario> buscarPorHotelYRango(Long hotelId, LocalDate desde, LocalDate hasta);
}
