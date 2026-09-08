package com.stayhub.inventarioytarifas.service;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.servicioDeOverbooking.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.servicioDeOverbooking.dto.DisponibilidadDTO;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;

/**
 * Adapta ServicioDeInventarioYTarifas al puerto de solo lectura que consume
 * ServicioDeOverbooking para buscar disponibilidad alternativa al resolver
 * un conflicto. Ver el Javadoc de ServicioDeInventarioYTarifasImpl para el
 * motivo por el que este mapeo vive en una clase aparte.
 */
@Stateless
public class AdaptadorPortOverbooking implements ServicioDeInventarioYTarifasPort {

    @Inject
    private ServicioDeInventarioYTarifas nucleo;

    @Override
    public List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta) {
        return nucleo.consultarDisponibilidad(hotelId, desde, hasta).stream()
                .map(d -> new DisponibilidadDTO(d.hotelId(), d.tipoHabitacion(), d.desde(), d.hasta(), d.unidadesDisponibles()))
                .toList();
    }
}
