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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * Implementa TRES interfaces:
 *  - ServicioDeInventarioYTarifas: el contrato propio, para InventarioResource
 *    y para dar de alta inventario/tarifas.
 *  - GestionDeDisponibilidadPort (com.stayhub.reservas.contrato): el contrato
 *    que espera ServicioDeReservas para pedir y soltar holds.
 *  - GestionDeHolds: el agregado propio para reemplazar un hold por otro sin
 *    que exista un instante en el que la reserva se quedó sin cupo retenido.
 *
 * No implementa acá los puertos de solo lectura ServicioDeInventarioYTarifasPort
 * de ServicioDeCanalesExternos y de ServicioDeOverbooking: ambos declaran un
 * consultarDisponibilidad(Long, LocalDate, LocalDate) con el mismo borrado de
 * firma pero devolviendo cada uno su propio DisponibilidadDTO -tipos de
 * retorno incompatibles entre sí- y Java no permite que una sola clase
 * implemente ambas interfaces a la vez. Ver AdaptadorPortCanalesExternos y
 * AdaptadorPortOverbooking, que delegan acá y solo traducen DTOs.
 *
 * CONCURRENCIA: todo método que decida en base al cupo y después lo escriba
 * bloquea antes las filas de InventarioDiario involucradas
 * (buscarPorHotelTipoYFechaBloqueando). El bloqueo se toma SIEMPRE en el mismo
 * orden -por (tipoHabitacion, fecha), ver ClaveDia- para que dos transacciones
 * que se cruzan no puedan quedar en deadlock esperándose mutuamente.
 */
@Stateless
public class ServicioDeInventarioYTarifasImpl
        implements ServicioDeInventarioYTarifas, GestionDeDisponibilidadPort {

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
            // Bloqueante: dos cargas simultáneas del mismo día se serializan en
            // vez de pisarse (la segunda actualiza sobre lo que dejó la primera).
            InventarioDiario dia = inventarioRepositorio
                    .buscarPorHotelTipoYFechaBloqueando(solicitud.hotelId(), solicitud.tipoHabitacion(), fechaActual)
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
        validarSolicitudDeHold(hotelId, tipoHabitacion, cantidadHabitaciones, checkIn, checkOut);

        List<ClaveDia> noches = noches(tipoHabitacion, checkIn, checkOut);
        Map<ClaveDia, InventarioDiario> dias = bloquearEnOrden(hotelId, noches);
        verificarCupo(hotelId, dias, noches, cantidadHabitaciones, Map.of());
        ocuparNoches(dias, noches, cantidadHabitaciones);

        return crearYGuardarHold(hotelId, tipoHabitacion, cantidadHabitaciones, checkIn, checkOut);
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

    /**
     * Devuelve el cupo retenido al inventario. Acepta un hold CONFIRMADO a
     * propósito: cancelar una reserva ya confirmada tiene que devolver el cupo.
     * Lo que NO hay que hacer con este método es implementar una modificación
     * como liberar + volver a pedir, porque entre las dos llamadas la reserva
     * queda sin retención y el cupo puede irse a otro pedido — para eso está
     * reemplazarHold.
     */
    @Override
    public void liberarHold(String holdId) {
        Hold hold = buscarHoldOFallar(holdId);
        if (hold.getEstado() == EstadoHold.LIBERADO) {
            return; // liberarHold es idempotente: no vuelve a devolver cupo ya devuelto.
        }
        List<ClaveDia> noches = noches(hold.getTipoHabitacion(), hold.getCheckIn(), hold.getCheckOut());
        Map<ClaveDia, InventarioDiario> dias = bloquearEnOrden(hold.getHotelId(), noches);
        liberarNoches(dias, noches, hold.getCantidadHabitaciones());

        hold.liberar();
        holdRepositorio.guardar(hold);
    }

    @Override
    public String reemplazarHold(String holdAnteriorId, Long hotelId, String tipoHabitacion,
                                 int cantidadHabitaciones, LocalDate checkIn, LocalDate checkOut) {
        validarSolicitudDeHold(hotelId, tipoHabitacion, cantidadHabitaciones, checkIn, checkOut);

        Hold anterior = buscarHoldOFallar(holdAnteriorId);
        if (!anterior.getHotelId().equals(hotelId)) {
            // Cambiar de hotel no es "modificar la retención": es otra reserva.
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                    "El hold " + holdAnteriorId + " pertenece al hotelId=" + anterior.getHotelId()
                            + " y no se puede reemplazar por uno del hotelId=" + hotelId);
        }

        boolean anteriorRetieneCupo = anterior.getEstado() != EstadoHold.LIBERADO;
        List<ClaveDia> nochesAnteriores = anteriorRetieneCupo
                ? noches(anterior.getTipoHabitacion(), anterior.getCheckIn(), anterior.getCheckOut())
                : List.of();
        List<ClaveDia> nochesNuevas = noches(tipoHabitacion, checkIn, checkOut);

        // Un único bloqueo ordenado sobre la unión de ambos rangos: si se
        // bloqueara primero un rango y después el otro, dos reemplazos cruzados
        // (A->B y B->A) podrían quedar esperándose para siempre.
        List<ClaveDia> todas = new ArrayList<>(nochesAnteriores);
        todas.addAll(nochesNuevas);
        Map<ClaveDia, InventarioDiario> dias = bloquearEnOrden(hotelId, todas);

        // Se verifica TODO el pedido nuevo antes de tocar una sola fila. No
        // alcanzaría con liberar primero y dejar que falle el ocupar: la
        // excepción que informa "no hay cupo" es SinDisponibilidadException,
        // anotada @ApplicationException(rollback = false) porque
        // ServicioDeReservas la atrapa y sigue usando la misma transacción —
        // así que lanzarla NO deshace la liberación previa, y el hold anterior
        // se quedaría sin su cupo. Verificando antes, el caso sin
        // disponibilidad sale de acá sin haber modificado nada.
        //
        // El crédito son las unidades que el hold anterior devolvería en cada
        // noche: una modificación que reusa las mismas noches (p. ej. pasar de
        // 2 a 3 habitaciones del mismo tipo) puede contar con ellas.
        Map<ClaveDia, Integer> credito = nochesAnteriores.stream()
                .collect(Collectors.toMap(Function.identity(), c -> anterior.getCantidadHabitaciones()));
        verificarCupo(hotelId, dias, nochesNuevas, cantidadHabitaciones, credito);

        liberarNoches(dias, nochesAnteriores, anterior.getCantidadHabitaciones());
        ocuparNoches(dias, nochesNuevas, cantidadHabitaciones);

        if (anteriorRetieneCupo) {
            anterior.liberar();
            holdRepositorio.guardar(anterior);
        }
        return crearYGuardarHold(hotelId, tipoHabitacion, cantidadHabitaciones, checkIn, checkOut);
    }

    // ------------------------------------------------------------------
    // helpers privados
    // ------------------------------------------------------------------

    /**
     * Identifica una fila de InventarioDiario dentro de un mismo hotel. El
     * orden natural (tipoHabitacion, fecha) es el orden global en el que se
     * toman los bloqueos: mientras todas las transacciones lo respeten, no
     * puede haber deadlock entre ellas.
     */
    private record ClaveDia(String tipoHabitacion, LocalDate fecha) implements Comparable<ClaveDia> {
        @Override
        public int compareTo(ClaveDia otra) {
            int porTipo = tipoHabitacion.compareTo(otra.tipoHabitacion);
            return porTipo != 0 ? porTipo : fecha.compareTo(otra.fecha);
        }
    }

    /** Una clave por cada noche del rango [checkIn, checkOut). */
    private static List<ClaveDia> noches(String tipoHabitacion, LocalDate checkIn, LocalDate checkOut) {
        List<ClaveDia> noches = new ArrayList<>();
        for (LocalDate fecha = checkIn; fecha.isBefore(checkOut); fecha = fecha.plusDays(1)) {
            noches.add(new ClaveDia(tipoHabitacion, fecha));
        }
        return noches;
    }

    /**
     * Bloquea las filas pedidas en el orden natural de ClaveDia y las devuelve
     * indexadas. Las claves sin fila cargada quedan afuera del mapa: quien
     * llama decide si eso es un error (ocuparNoches) o algo a ignorar
     * (liberarNoches).
     */
    private Map<ClaveDia, InventarioDiario> bloquearEnOrden(Long hotelId, Collection<ClaveDia> claves) {
        Map<ClaveDia, InventarioDiario> bloqueadas = new LinkedHashMap<>();
        for (ClaveDia clave : new TreeSet<>(claves)) {
            inventarioRepositorio
                    .buscarPorHotelTipoYFechaBloqueando(hotelId, clave.tipoHabitacion(), clave.fecha())
                    .ifPresent(dia -> bloqueadas.put(clave, dia));
        }
        return bloqueadas;
    }

    /**
     * Verifica el cupo de TODAS las noches sin tocar ninguna, para que un rango
     * sin disponibilidad en su última noche no deje las anteriores ya ocupadas
     * ni ninguna otra escritura a medio hacer.
     *
     * @param credito unidades que quien llama va a devolver en esa misma noche
     *                antes de ocupar (ver reemplazarHold); vacío en el alta común.
     */
    private void verificarCupo(Long hotelId, Map<ClaveDia, InventarioDiario> dias, List<ClaveDia> noches,
                               int cantidadHabitaciones, Map<ClaveDia, Integer> credito) {
        for (ClaveDia noche : noches) {
            InventarioDiario dia = dias.get(noche);
            if (dia == null) {
                throw new SinDisponibilidadException("No hay inventario cargado para hotelId=" + hotelId
                        + ", tipoHabitacion=" + noche.tipoHabitacion() + " el " + noche.fecha());
            }
            if (dia.getUnidadesDisponibles() + credito.getOrDefault(noche, 0) < cantidadHabitaciones) {
                throw new SinDisponibilidadException("No hay disponibilidad para hotelId=" + hotelId
                        + ", tipoHabitacion=" + noche.tipoHabitacion() + " el " + noche.fecha());
            }
        }
    }

    /** Solo mutación: el cupo ya tiene que haber pasado por verificarCupo. */
    private void ocuparNoches(Map<ClaveDia, InventarioDiario> dias, List<ClaveDia> noches,
                              int cantidadHabitaciones) {
        for (ClaveDia noche : noches) {
            InventarioDiario dia = dias.get(noche);
            dia.ocupar(cantidadHabitaciones);
            inventarioRepositorio.guardar(dia);
        }
    }

    private void liberarNoches(Map<ClaveDia, InventarioDiario> dias, List<ClaveDia> noches,
                               int cantidadHabitaciones) {
        for (ClaveDia noche : noches) {
            InventarioDiario dia = dias.get(noche);
            if (dia != null) {
                dia.liberar(cantidadHabitaciones);
                inventarioRepositorio.guardar(dia);
            }
        }
    }

    private String crearYGuardarHold(Long hotelId, String tipoHabitacion, int cantidadHabitaciones,
                                     LocalDate checkIn, LocalDate checkOut) {
        Hold hold = new Hold(UUID.randomUUID().toString(), hotelId, tipoHabitacion,
                cantidadHabitaciones, checkIn, checkOut);
        holdRepositorio.guardar(hold);
        return hold.getId();
    }

    private Hold buscarHoldOFallar(String holdId) {
        return holdRepositorio.buscarPorId(holdId)
                .orElseThrow(() -> new InventarioTarifasException(CodigoErrorInventarioTarifas.HOLD_NO_ENCONTRADO,
                        "No existe un hold con id " + holdId));
    }

    /**
     * Sin esta validación, un pedido con cantidad 0 o con checkOut <= checkIn
     * recorría cero noches, no descontaba nada y aun así devolvía un holdId
     * válido: un hold fantasma que después "libera" cupo que nunca retuvo.
     */
    private void validarSolicitudDeHold(Long hotelId, String tipoHabitacion, int cantidadHabitaciones,
                                        LocalDate checkIn, LocalDate checkOut) {
        if (hotelId == null || tipoHabitacion == null || tipoHabitacion.isBlank()
                || cantidadHabitaciones < 1 || checkIn == null || checkOut == null
                || !checkOut.isAfter(checkIn)) {
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.SOLICITUD_INVALIDA,
                    "El pedido de hold está incompleto o contiene valores inválidos"
                            + " (hotelId / tipoHabitacion / cantidadHabitaciones >= 1 / checkIn < checkOut)");
        }
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
