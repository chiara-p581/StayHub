package com.stayhub.canalesexternos.service;

import com.stayhub.canalesexternos.contrato.ServicioDeCanalesExternos;
import com.stayhub.canalesexternos.contrato.interno.*;
import com.stayhub.canalesexternos.dto.*;
import com.stayhub.canalesexternos.exception.*;
import com.stayhub.canalesexternos.messaging.PublicadorSincronizacion;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;

@Stateless
public class ServicioDeCanalesExternosImpl implements ServicioDeCanalesExternos {
    @Inject Instance<ServicioDeReservasPort> reservas;
    @Inject Instance<ServicioDeInventarioYTarifasPort> inventarioYTarifas;
    @Inject PublicadorSincronizacion publicadorSincronizacion;

    @Override
    public List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta) {
        validarPeriodo(hotelId, desde, hasta);
        return inventario().consultarDisponibilidad(hotelId, desde, hasta);
    }

    @Override
    public ResultadoReservaDTO recibirReserva(ReservaExternaDTO reserva) {
        validarReserva(reserva);
        ResultadoOperacionReserva resultado = reservas().crearDesdeCanal(mapear(reserva));
        return resultado(reserva, resultado);
    }

    @Override
    public ResultadoReservaDTO modificarReserva(ReservaExternaDTO reserva) {
        validarReserva(reserva);
        ResultadoOperacionReserva resultado = reservas().modificarDesdeCanal(mapear(reserva));
        return resultado(reserva, resultado);
    }

    @Override
    public ResultadoReservaDTO cancelarReserva(Canal canal, String idExterno) {
        if (canal == null || idExterno == null || idExterno.isBlank()) invalida("Canal e idExterno son obligatorios");
        ResultadoOperacionReserva resultado = reservas().cancelarDesdeCanal(canal.name(), idExterno);
        return new ResultadoReservaDTO(resultado.reservaId(), idExterno, canal, resultado.estado());
    }

    @Override
    public ResultadoSincronizacionDTO sincronizarOta(Long hotelId, Canal canal, LocalDate desde, LocalDate hasta) {
        validarPeriodo(hotelId, desde, hasta);
        if (canal == null) invalida("El canal es obligatorio");
        publicadorSincronizacion.publicarOta(hotelId, canal, desde, hasta);
        return ResultadoSincronizacionDTO.encolado(canal.name());
    }

    @Override
    public ResultadoSincronizacionDTO sincronizarPms(Long hotelId, LocalDate desde, LocalDate hasta) {
        validarPeriodo(hotelId, desde, hasta);
        publicadorSincronizacion.publicarPms(hotelId, desde, hasta);
        return ResultadoSincronizacionDTO.encolado("PMS");
    }

    private ServicioDeReservasPort reservas() {
        if (!reservas.isResolvable()) throw dependencia("ServicioDeReservas");
        return reservas.get();
    }
    private ServicioDeInventarioYTarifasPort inventario() {
        if (!inventarioYTarifas.isResolvable()) throw dependencia("ServicioDeInventarioYTarifas");
        return inventarioYTarifas.get();
    }
    private CanalExternoException dependencia(String nombre) {
        return new CanalExternoException(CodigoErrorCanal.DEPENDENCIA_NO_DISPONIBLE,
                nombre + " todavía no posee una implementación disponible");
    }
    private SolicitudReserva mapear(ReservaExternaDTO r) {
        return new SolicitudReserva(r.idExterno(),r.canal().name(),r.hotelId(),r.checkIn(),r.checkOut(),
                r.tipoHabitacion(),r.cantidadHabitaciones(),r.huesped(),r.precioTotal(),r.moneda());
    }
    private ResultadoReservaDTO resultado(ReservaExternaDTO r, ResultadoOperacionReserva resultado) {
        return new ResultadoReservaDTO(resultado.reservaId(),r.idExterno(),r.canal(),resultado.estado());
    }
    private void validarPeriodo(Long hotelId, LocalDate desde, LocalDate hasta) {
        if (hotelId == null || desde == null || hasta == null || !hasta.isAfter(desde))
            invalida("hotelId y un período válido son obligatorios");
    }
    private void validarReserva(ReservaExternaDTO r) {
        if (r == null || r.idExterno() == null || r.idExterno().isBlank() || r.canal() == null ||
                r.hotelId() == null || r.checkIn() == null || r.checkOut() == null ||
                !r.checkOut().isAfter(r.checkIn()) || r.tipoHabitacion() == null ||
                r.tipoHabitacion().isBlank() || r.cantidadHabitaciones() < 1 || r.huesped() == null ||
                r.precioTotal() == null || r.precioTotal().signum() < 0 || r.moneda() == null || r.moneda().isBlank())
            invalida("La reserva externa está incompleta o contiene valores inválidos");
    }
    private void invalida(String mensaje) {
        throw new CanalExternoException(CodigoErrorCanal.SOLICITUD_INVALIDA, mensaje);
    }
}
