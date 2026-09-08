package com.stayhub.inventarioytarifas.service;

import com.stayhub.canalesexternos.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.canalesexternos.dto.DisponibilidadDTO;
import com.stayhub.canalesexternos.dto.TarifaDTO;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;

/**
 * Adapta ServicioDeInventarioYTarifas al puerto de solo lectura que consume
 * ServicioDeCanalesExternos para publicar disponibilidad y tarifas en las
 * OTAs. Ver el Javadoc de ServicioDeInventarioYTarifasImpl para el motivo
 * por el que este mapeo vive en una clase aparte.
 */
@Stateless
public class AdaptadorPortCanalesExternos implements ServicioDeInventarioYTarifasPort {

    @Inject
    private ServicioDeInventarioYTarifas nucleo;

    @Override
    public List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta) {
        return nucleo.consultarDisponibilidad(hotelId, desde, hasta).stream()
                .map(d -> new DisponibilidadDTO(d.hotelId(), d.tipoHabitacion(), d.desde(), d.hasta(), d.unidadesDisponibles()))
                .toList();
    }

    @Override
    public List<TarifaDTO> consultarTarifas(Long hotelId, LocalDate desde, LocalDate hasta) {
        return nucleo.consultarTarifas(hotelId, desde, hasta).stream()
                .map(t -> new TarifaDTO(t.hotelId(), t.tipoHabitacion(), t.desde(), t.hasta(), t.importe(), t.moneda()))
                .toList();
    }
}
