package com.stayhub.inventarioytarifas.contrato;

import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import com.stayhub.inventarioytarifas.dto.CargaInventarioResponse;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import java.time.LocalDate;
import java.util.List;

/** Operaciones propias de ServicioDeInventarioYTarifas, expuestas por InventarioResource. */
public interface ServicioDeInventarioYTarifas {
    List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta);
    List<TarifaDTO> consultarTarifas(Long hotelId, LocalDate desde, LocalDate hasta);
    CargaInventarioResponse cargarInventario(CargaInventarioRequest solicitud);
}
