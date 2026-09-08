package com.stayhub.canalesexternos.contrato;

import com.stayhub.canalesexternos.dto.*;
import java.time.LocalDate;
import java.util.List;

public interface ServicioDeCanalesExternos {
    List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta);
    ResultadoReservaDTO recibirReserva(ReservaExternaDTO reserva);
    ResultadoReservaDTO modificarReserva(ReservaExternaDTO reserva);
    ResultadoReservaDTO cancelarReserva(Canal canal, String idExterno);
    ResultadoSincronizacionDTO sincronizarOta(Long hotelId, Canal canal, LocalDate desde, LocalDate hasta);
    ResultadoSincronizacionDTO sincronizarPms(Long hotelId, LocalDate desde, LocalDate hasta);
}
