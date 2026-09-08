package com.stayhub.inventarioytarifas.service;

import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import com.stayhub.inventarioytarifas.dto.CargaInventarioResponse;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.inventarioytarifas.model.InventarioDiario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class InventarioTarifasMapper {

    private InventarioTarifasMapper() { }

    /**
     * Agrupa el inventario diario por tipo de habitación y arma una fila de
     * disponibilidad por rango. La disponibilidad de un rango es la mínima
     * entre todos sus días: es la cantidad de habitaciones que se pueden
     * garantizar para toda la estadía, no solo para un día suelto.
     */
    static List<DisponibilidadDTO> aDisponibilidad(List<InventarioDiario> dias, LocalDate desde, LocalDate hasta) {
        return porTipoHabitacion(dias).entrySet().stream()
                .map(e -> new DisponibilidadDTO(
                        e.getValue().get(0).getHotelId(),
                        e.getKey(),
                        desde,
                        hasta,
                        e.getValue().stream().mapToInt(InventarioDiario::getUnidadesDisponibles).min().orElse(0)))
                .toList();
    }

    /** El importe de un rango es la suma de la tarifa de cada uno de sus días. */
    static List<TarifaDTO> aTarifas(List<InventarioDiario> dias, LocalDate desde, LocalDate hasta) {
        return porTipoHabitacion(dias).entrySet().stream()
                .map(e -> new TarifaDTO(
                        e.getValue().get(0).getHotelId(),
                        e.getKey(),
                        desde,
                        hasta,
                        e.getValue().stream().map(InventarioDiario::getPrecio).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().get(0).getMoneda()))
                .toList();
    }

    static CargaInventarioResponse aCargaResponse(CargaInventarioRequest solicitud, int diasCargados) {
        return new CargaInventarioResponse(solicitud.hotelId(), solicitud.tipoHabitacion(),
                solicitud.desde(), solicitud.hasta(), diasCargados);
    }

    private static Map<String, List<InventarioDiario>> porTipoHabitacion(List<InventarioDiario> dias) {
        return dias.stream().collect(Collectors.groupingBy(InventarioDiario::getTipoHabitacion));
    }
}
