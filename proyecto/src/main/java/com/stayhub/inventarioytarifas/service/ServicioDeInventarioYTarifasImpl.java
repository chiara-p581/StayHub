package com.stayhub.inventarioytarifas.service;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import com.stayhub.inventarioytarifas.dto.CargaInventarioResponse;
import com.stayhub.inventarioytarifas.dto.DisponibilidadDTO;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.inventarioytarifas.exception.CodigoErrorInventarioTarifas;
import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;
import com.stayhub.inventarioytarifas.model.EstadoHold;
import com.stayhub.inventarioytarifas.model.Hold;
import com.stayhub.inventarioytarifas.model.InventarioDiario;
import com.stayhub.inventarioytarifas.repository.HoldRepository;
import com.stayhub.inventarioytarifas.repository.InventarioDiarioRepository;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.contrato.SinDisponibilidadException;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Núcleo de ServicioDeInventarioYTarifas.
 *
 * Es el único componente "stateful" del sistema (ver READMEs de
 * ServicioDeReservas y ServicioDeCanalesExternos): un hold creado en
 * crearHold sigue existiendo -y sigue descontando cupo de InventarioDiario-
 * hasta que una llamada posterior e independiente lo confirma o lo libera.
 * Ese estado no vive en memoria (esto sigue siendo un @Stateless, como el
 * resto de los servicios del proyecto) sino en la entidad Hold, persistida
 * vía HoldRepository.
 *
 * Implementa DOS interfaces:
 *  - ServicioDeInventarioYTarifas: el contrato propio, para InventarioResource
 *    y para dar de alta inventario/tarifas.
 *  - GestionDeDisponibilidadPort (com.stayhub.reservas.contrato): el contrato
 *    que espera ServicioDeReservas para pedir y soltar holds.
 *
 * No implementa acá los puertos de solo lectura ServicioDeInventarioYTarifasPort
 * de ServicioDeCanalesExternos y de ServicioDeOverbooking: ambos declaran un
 * consultarDisponibilidad(Long, LocalDate, LocalDate) con el mismo borrado de
 * firma pero devolviendo cada uno su propio DisponibilidadDTO -tipos de
 * retorno incompatibles entre sí- y Java no permite que una sola clase
 * implemente ambas interfaces a la vez. Ver AdaptadorPortCanalesExternos y
 * AdaptadorPortOverbooking, que delegan acá y solo traducen DTOs.
 */
@Stateless
public class ServicioDeInventarioYTarifasImpl implements ServicioDeInventarioYTarifas, GestionDeDisponibilidadPort {

    @Inject
    private InventarioDiarioRepository inventarioRepositorio;

    @Inject
    private HoldRepository holdRepositorio;

    // ------------------------------------------------------------------
    // ServicioDeInventarioYTarifas
    // ------------------------------------------------------------------

    @Override
    public List<DisponibilidadDTO> consultarDisponibilidad(Long hotelId, LocalDate desde, LocalDate hasta) {
        validarPeriodo(hotelId, desde, hasta);
        return InventarioTarifasMapper.aDisponibilidad(
                inventarioRepositorio.buscarPorHotelYRango(hotelId, desde, hasta), desde, hasta);
    }

    @Override
    public List<TarifaDTO> consultarTarifas(Long hotelId, LocalDate desde, LocalDate hasta) {
        validarPeriodo(hotelId, desde, hasta);
        return InventarioTarifasMapper.aTarifas(
                inventarioRepositorio.buscarPorHotelYRango(hotelId, desde, hasta), desde, hasta);
    }

    @Override
    public CargaInventarioResponse cargarInventario(CargaInventarioRequest solicitud) {
        validarCarga(solicitud);
        int diasCargados = 0;
        for (LocalDate fecha = solicitud.desde(); fecha.isBefore(solicitud.hasta()); fecha = fecha.plusDays(1)) {
            LocalDate fechaActual = fecha;
            InventarioDiario dia = inventarioRepositorio
                    .buscarPorHotelTipoYFecha(solicitud.hotelId(), solicitud.tipoHabitacion(), fechaActual)
                    .orElseGet(() -> new InventarioDiario(solicitud.hotelId(), solicitud.tipoHabitacion(), fechaActual,
                            solicitud.unidadesTotales(), solicitud.precio(), solicitud.moneda()));
            dia.actualizarCapacidadYTarifa(solicitud.unidadesTotales(), solicitud.precio(), solicitud.moneda());
            inventarioRepositorio.guardar(dia);
            diasCargados++;
        }
        return InventarioTarifasMapper.aCargaResponse(solicitud, diasCargados);
    }

    // ------------------------------------------------------------------
    // GestionDeDisponibilidadPort (consumido por ServicioDeReservas)
    // ------------------------------------------------------------------

    @Override
    public String crearHold(Long hotelId, String tipoHabitacion, int cantidadHabitaciones,
                             LocalDate checkIn, LocalDate checkOut) {
        List<InventarioDiario> dias = diasConCupo(hotelId, tipoHabitacion, checkIn, checkOut, cantidadHabitaciones);
        dias.forEach(dia -> dia.ocupar(cantidadHabitaciones));
        dias.forEach(inventarioRepositorio::guardar);

        Hold hold = new Hold(UUID.randomUUID().toString(), hotelId, tipoHabitacion,
                cantidadHabitaciones, checkIn, checkOut);
        holdRepositorio.guardar(hold);
        return hold.getId();
    }

    @Override
    public void confirmarHold(String holdId) {
        Hold hold = buscarHoldOFallar(holdId);
        if (hold.getEstado() != EstadoHold.PENDIENTE) {
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.TRANSICION_DE_ESTADO_INVALIDA,
                    "Solo se puede confirmar un hold PENDIENTE (estado actual: " + hold.getEstado() + ")");
        }
        hold.confirmar();
        holdRepositorio.guardar(hold);
    }

    @Override
    public void liberarHold(String holdId) {
        Hold hold = buscarHoldOFallar(holdId);
        if (hold.getEstado() == EstadoHold.LIBERADO) {
            return; // liberarHold es idempotente: no vuelve a descontar cupo ya devuelto.
        }
        for (LocalDate fecha = hold.getCheckIn(); fecha.isBefore(hold.getCheckOut()); fecha = fecha.plusDays(1)) {
            inventarioRepositorio.buscarPorHotelTipoYFecha(hold.getHotelId(), hold.getTipoHabitacion(), fecha)
                    .ifPresent(dia -> {
                        dia.liberar(hold.getCantidadHabitaciones());
                        inventarioRepositorio.guardar(dia);
                    });
        }
        hold.liberar();
        holdRepositorio.guardar(hold);
    }

    // ------------------------------------------------------------------
    // helpers privados
    // ------------------------------------------------------------------

    private List<InventarioDiario> diasConCupo(Long hotelId, String tipoHabitacion, LocalDate checkIn,
                                                LocalDate checkOut, int cantidadHabitaciones) {
        List<InventarioDiario> dias = new ArrayList<>();
        for (LocalDate fecha = checkIn; fecha.isBefore(checkOut); fecha = fecha.plusDays(1)) {
            LocalDate fechaActual = fecha;
            InventarioDiario dia = inventarioRepositorio.buscarPorHotelTipoYFecha(hotelId, tipoHabitacion, fechaActual)
                    .orElseThrow(() -> new SinDisponibilidadException(
                            "No hay inventario cargado para hotelId=" + hotelId
                                    + ", tipoHabitacion=" + tipoHabitacion + " el " + fechaActual));
            if (dia.getUnidadesDisponibles() < cantidadHabitaciones) {
                throw new SinDisponibilidadException("No hay disponibilidad para hotelId=" + hotelId
                        + ", tipoHabitacion=" + tipoHabitacion + " el " + fechaActual);
            }
            dias.add(dia);
        }
        return dias;
    }

    private Hold buscarHoldOFallar(String holdId) {
        return holdRepositorio.buscarPorId(holdId)
                .orElseThrow(() -> new InventarioTarifasException(CodigoErrorInventarioTarifas.HOLD_NO_ENCONTRADO,
                        "No existe un hold con id " + holdId));
    }

    private void validarPeriodo(Long hotelId, LocalDate desde, LocalDate hasta) {
        if (hotelId == null || desde == null || hasta == null || !hasta.isAfter(desde)) {
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                    "hotelId y un período válido son obligatorios");
        }
    }

    private void validarCarga(CargaInventarioRequest s) {
        if (s == null || s.hotelId() == null || s.tipoHabitacion() == null || s.tipoHabitacion().isBlank()
                || s.desde() == null || s.hasta() == null || !s.hasta().isAfter(s.desde())
                || s.unidadesTotales() < 0 || s.precio() == null || s.precio().compareTo(BigDecimal.ZERO) < 0
                || s.moneda() == null || s.moneda().isBlank()) {
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                    "La carga de inventario está incompleta o contiene valores inválidos");
        }
    }
}
