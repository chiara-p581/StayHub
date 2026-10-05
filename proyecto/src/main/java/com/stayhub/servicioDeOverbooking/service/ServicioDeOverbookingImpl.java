package com.stayhub.servicioDeOverbooking.service;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.contrato.SinDisponibilidadException;
import com.stayhub.servicioDeOverbooking.contrato.ServicioDeOverbooking;
import com.stayhub.servicioDeOverbooking.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.servicioDeOverbooking.dto.ConflictoReservaDTO;
import com.stayhub.servicioDeOverbooking.dto.DisponibilidadDTO;
import com.stayhub.servicioDeOverbooking.dto.ResultadoResolucionOverbookingDTO;
import com.stayhub.servicioDeOverbooking.exception.CodigoErrorOverbooking;
import com.stayhub.servicioDeOverbooking.exception.OverbookingException;
import com.stayhub.servicioDeOverbooking.messaging.PublicadorSolicitudNotificacionAsincrona;
import com.stayhub.servicioDeOverbooking.messaging.SolicitudNotificacionAsincrona;
import com.stayhub.servicioDeOverbooking.model.ConflictoOverbooking;
import com.stayhub.servicioDeOverbooking.repository.ConflictoOverbookingRepository;

import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@Stateless
public class ServicioDeOverbookingImpl implements ServicioDeOverbooking {

    @Inject
    private ConflictoOverbookingRepository repositorio;

    @Inject
    private Instance<ServicioDeInventarioYTarifasPort> inventarioLectura;

    @Inject
    private Instance<GestionDeDisponibilidadPort> disponibilidad;

    @Inject
    private PublicadorSolicitudNotificacionAsincrona publicadorNotificacion;

    @Override
    public ResultadoResolucionOverbookingDTO resolverConflicto(
            ConflictoReservaDTO dto
    ) {
        validar(dto);

        ConflictoOverbooking conflicto = OverbookingMapper.nuevo(dto);

        resolver(conflicto);

        repositorio.guardar(conflicto);
        notificarSiCorresponde(conflicto);

        return OverbookingMapper.aResultado(conflicto);
    }

    private void resolver(ConflictoOverbooking conflicto) {
        List<DisponibilidadDTO> candidatos =
                inventarioLectura().consultarDisponibilidad(
                        conflicto.getHotelId(),
                        conflicto.getCheckIn(),
                        conflicto.getCheckOut()
                );

        Optional<DisponibilidadDTO> alternativa = candidatos.stream()
                .filter(d ->
                        !d.tipoHabitacion().equals(
                                conflicto.getTipoHabitacion()
                        )
                )
                .filter(d ->
                        d.unidadesDisponibles()
                                >= conflicto.getCantidadHabitaciones()
                )
                .findFirst();

        if (alternativa.isEmpty()) {
            conflicto.resolverPorCompensacion(
                    "Sin disponibilidad alternativa en el hotel para el período: "
                            + "se ofrece compensación"
            );
            return;
        }

        try {
            String holdId = disponibilidad().crearHold(
                    conflicto.getHotelId(),
                    alternativa.get().tipoHabitacion(),
                    conflicto.getCantidadHabitaciones(),
                    conflicto.getCheckIn(),
                    conflicto.getCheckOut()
            );

            disponibilidad().confirmarHold(holdId);

            conflicto.resolverPorReubicacion(
                    alternativa.get().tipoHabitacion(),
                    holdId,
                    "Reubicado a tipoHabitacion="
                            + alternativa.get().tipoHabitacion()
            );

        } catch (SinDisponibilidadException ex) {
            conflicto.resolverPorCompensacion(
                    "La alternativa dejó de estar disponible al intentar "
                            + "retenerla: se ofrece compensación"
            );
        }
    }

    private ServicioDeInventarioYTarifasPort inventarioLectura() {
        if (!inventarioLectura.isResolvable()) {
            throw new OverbookingException(
                    CodigoErrorOverbooking.DEPENDENCIA_NO_DISPONIBLE,
                    "ServicioDeInventarioYTarifas todavía no posee "
                            + "una implementación disponible"
            );
        }

        return inventarioLectura.get();
    }

    private GestionDeDisponibilidadPort disponibilidad() {
        if (!disponibilidad.isResolvable()) {
            throw new OverbookingException(
                    CodigoErrorOverbooking.DEPENDENCIA_NO_DISPONIBLE,
                    "ServicioDeInventarioYTarifas todavía no posee "
                            + "una implementación disponible"
            );
        }

        return disponibilidad.get();
    }

    private void notificarSiCorresponde(ConflictoOverbooking conflicto) {
        if (conflicto.getHuespedEmail() == null
                || conflicto.getHuespedEmail().isBlank()) {
            return;
        }

        SolicitudNotificacionAsincrona solicitud =
                new SolicitudNotificacionAsincrona(
                        conflicto.getHuespedEmail(),
                        TipoEvento.RESULTADO_OVERBOOKING,
                        CanalNotificacion.EMAIL,
                        "Cambio en tu reserva",
                        conflicto.getMensaje()
                );

        publicadorNotificacion.publicar(solicitud);
    }

    private void validar(ConflictoReservaDTO dto) {
        if (dto == null
                || dto.reservaIdConflictiva() == null
                || dto.hotelId() == null
                || dto.tipoHabitacion() == null
                || dto.tipoHabitacion().isBlank()
                || dto.cantidadHabitaciones() < 1
                || dto.checkIn() == null
                || dto.checkOut() == null
                || !dto.checkOut().isAfter(dto.checkIn())) {

            throw new OverbookingException(
                    CodigoErrorOverbooking.SOLICITUD_INVALIDA,
                    "Faltan datos obligatorios o inválidos del conflicto "
                            + "(hotelId / tipoHabitacion / cantidadHabitaciones "
                            + "/ checkIn-checkOut)"
            );
        }
    }
}