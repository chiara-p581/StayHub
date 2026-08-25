package com.stayhub.servicioDeOverbooking.service;

import com.stayhub.notificaciones.contrato.ServicioDeNotificaciones;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.servicioDeOverbooking.contrato.ServicioDeOverbooking;
import com.stayhub.servicioDeOverbooking.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.servicioDeOverbooking.dto.ConflictoReservaDTO;
import com.stayhub.servicioDeOverbooking.dto.ResultadoResolucionOverbookingDTO;
import com.stayhub.servicioDeOverbooking.exception.CodigoErrorOverbooking;
import com.stayhub.servicioDeOverbooking.exception.OverbookingException;
import com.stayhub.servicioDeOverbooking.model.ConflictoOverbooking;
import com.stayhub.servicioDeOverbooking.model.EstrategiaResolucion;
import com.stayhub.servicioDeOverbooking.repository.ConflictoOverbookingRepository;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Stateless
public class ServicioDeOverbookingImpl implements ServicioDeOverbooking {

    @Inject private ConflictoOverbookingRepository repositorio;
    @Inject private Instance<ServicioDeInventarioYTarifasPort> inventarioYTarifas;
    @Inject private Instance<ServicioDeNotificaciones> notificaciones;

    @Override
    public ResultadoResolucionOverbookingDTO resolverConflicto(ConflictoReservaDTO dto) {
        validar(dto);

        ConflictoOverbooking conflicto = OverbookingMapper.nuevo(dto);

        // TODO: cuando ServicioDeInventarioYTarifas esté disponible, usar
        // inventario() para buscar una habitación/hotel alternativo real
        // en lugar de asumir REUBICACION por defecto.
        conflicto.resolver(EstrategiaResolucion.REUBICACION, null,
                "Conflicto resuelto por reubicación (lógica preliminar, pendiente de integración con Inventario y Tarifas)");

        repositorio.guardar(conflicto);
        notificarSiDisponible(conflicto);

        return OverbookingMapper.aResultado(conflicto);
    }

    private ServicioDeInventarioYTarifasPort inventario() {
        if (!inventarioYTarifas.isResolvable())
            throw new OverbookingException(CodigoErrorOverbooking.DEPENDENCIA_NO_DISPONIBLE,
                    "ServicioDeInventarioYTarifas todavía no posee una implementación disponible");
        return inventarioYTarifas.get();
    }

    private void notificarSiDisponible(ConflictoOverbooking conflicto) {
        if (!notificaciones.isResolvable()) return; // best-effort
        SolicitudNotificacionDTO solicitud = new SolicitudNotificacionDTO(
                conflicto.getReferenciaExterna(),
                TipoEvento.RESULTADO_OVERBOOKING,
                CanalNotificacion.EMAIL,
                "Cambio en tu reserva",
                conflicto.getMensaje()
        );
        notificaciones.get().enviar(solicitud);
    }

    private void validar(ConflictoReservaDTO dto) {
        if (dto == null || dto.reservaIdConflictiva() == null || dto.hotelId() == null)
            throw new OverbookingException(CodigoErrorOverbooking.SOLICITUD_INVALIDA,
                    "Faltan datos obligatorios del conflicto (reservaIdConflictiva / hotelId)");
    }
}