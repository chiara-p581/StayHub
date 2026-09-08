package com.stayhub.canalesexternos.contrato.interno;

import com.stayhub.canalesexternos.dto.DisponibilidadDTO;
import com.stayhub.canalesexternos.dto.TarifaDTO;
import java.time.LocalDate;
import java.util.List;

/** Contrato de lectura que deberá implementar ServicioDeInventarioYTarifas. */
public interface ServicioDeInventarioYTarifasPort {
    List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta);
    List<TarifaDTO> consultarTarifas(Long hotelId, LocalDate desde, LocalDate hasta);
}
