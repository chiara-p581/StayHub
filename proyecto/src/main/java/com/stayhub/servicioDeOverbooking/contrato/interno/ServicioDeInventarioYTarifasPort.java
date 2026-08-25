package com.stayhub.servicioDeOverbooking.contrato.interno;

import com.stayhub.servicioDeOverbooking.dto.DisponibilidadDTO;
import java.time.LocalDate;
import java.util.List;

public interface ServicioDeInventarioYTarifasPort {
    List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta);
}