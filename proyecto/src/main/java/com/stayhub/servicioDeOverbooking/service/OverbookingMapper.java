package com.stayhub.servicioDeOverbooking.service;

import com.stayhub.servicioDeOverbooking.dto.ConflictoReservaDTO;
import com.stayhub.servicioDeOverbooking.dto.ResultadoResolucionOverbookingDTO;
import com.stayhub.servicioDeOverbooking.model.ConflictoOverbooking;
import java.time.OffsetDateTime;

final class OverbookingMapper {

    private OverbookingMapper() { }

    static ConflictoOverbooking nuevo(ConflictoReservaDTO dto) {
        return new ConflictoOverbooking(dto.reservaIdConflictiva(), dto.hotelId(), dto.tipoHabitacion(),
                dto.cantidadHabitaciones(), dto.checkIn(), dto.checkOut(), dto.canalOrigen(),
                dto.referenciaExterna(), dto.huespedEmail());
    }

    static ResultadoResolucionOverbookingDTO aResultado(ConflictoOverbooking c) {
        return new ResultadoResolucionOverbookingDTO(c.getId(), c.getReservaIdConflictiva(), c.isResuelto(),
                c.getEstrategiaAplicada(), c.getTipoHabitacionAlternativa(), c.getHoldAlternativoId(),
                c.getMensaje(), OffsetDateTime.now());
    }
}