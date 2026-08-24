package com.stayhub.hoteles.service;

import com.stayhub.hoteles.contrato.ServicioDeHotelesPort;
import com.stayhub.hoteles.dto.*;
import com.stayhub.hoteles.exception.*;
import com.stayhub.hoteles.model.*;
import com.stayhub.hoteles.repository.HotelRepository;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.*;

import static com.stayhub.hoteles.exception.CodigoErrorHotel.*;

@Stateless
public class ServicioDeHotelesImpl implements ServicioDeHoteles, ServicioDeHotelesPort {
    @Inject private HotelRepository repositorio;

    @Override
    public HotelResponse crearHotel(HotelRequest s) {
        validarHotel(s);
        Hotel hotel = new Hotel(limpiar(s.nombre()), limpiar(s.direccion()), limpiar(s.ciudad()),
                limpiar(s.pais()), limpiarOpcional(s.descripcion()), normalizar(s.servicios()));
        repositorio.guardar(hotel);
        return respuesta(hotel);
    }

    @Override
    public HotelResponse modificarHotel(Long id, HotelRequest s) {
        validarHotel(s);
        Hotel hotel = hotel(id);
        hotel.actualizar(limpiar(s.nombre()), limpiar(s.direccion()), limpiar(s.ciudad()), limpiar(s.pais()),
                limpiarOpcional(s.descripcion()), normalizar(s.servicios()));
        repositorio.guardar(hotel);
        return respuesta(hotel);
    }

    @Override public HotelResponse consultarHotel(Long id) { return respuesta(hotel(id)); }

    @Override
    public List<HotelResponse> listarHoteles(boolean incluirInactivos) {
        return repositorio.listarHoteles(incluirInactivos).stream().map(this::respuesta).toList();
    }

    @Override
    public HotelResponse darDeBajaHotel(Long id) {
        Hotel hotel = hotel(id);
        hotel.darDeBaja();
        repositorio.listarHabitaciones(id).forEach(h -> { h.darDeBaja(); repositorio.guardar(h); });
        repositorio.listarTipos(id).forEach(t -> { t.darDeBaja(); repositorio.guardar(t); });
        repositorio.guardar(hotel);
        return respuesta(hotel);
    }

    @Override
    public TipoHabitacionResponse crearTipo(Long hotelId, TipoHabitacionRequest s) {
        Hotel hotel = hotelActivo(hotelId);
        validarTipo(s);
        String codigo = codigo(s.codigo());
        if (repositorio.buscarTipoPorCodigo(hotelId, codigo).isPresent())
            throw error(CODIGO_DUPLICADO, "Ya existe el tipo " + codigo + " en el hotel " + hotelId);
        TipoHabitacion tipo = new TipoHabitacion(hotel, codigo, limpiar(s.nombre()), limpiarOpcional(s.descripcion()),
                s.capacidadMaxima(), normalizar(s.caracteristicas()));
        return HotelMapper.tipo(repositorio.guardar(tipo));
    }

    @Override
    public TipoHabitacionResponse modificarTipo(Long hotelId, Long tipoId, TipoHabitacionRequest s) {
        hotelActivo(hotelId);
        validarTipo(s);
        TipoHabitacion tipo = tipoDelHotel(hotelId, tipoId);
        String codigo = codigo(s.codigo());
        repositorio.buscarTipoPorCodigo(hotelId, codigo)
                .filter(otro -> !otro.getId().equals(tipoId))
                .ifPresent(otro -> { throw error(CODIGO_DUPLICADO, "Ya existe el tipo " + codigo); });
        tipo.actualizar(codigo, limpiar(s.nombre()), limpiarOpcional(s.descripcion()), s.capacidadMaxima(),
                normalizar(s.caracteristicas()));
        return HotelMapper.tipo(repositorio.guardar(tipo));
    }

    @Override
    public void darDeBajaTipo(Long hotelId, Long tipoId) {
        TipoHabitacion tipo = tipoDelHotel(hotelId, tipoId);
        if (repositorio.contarHabitacionesActivasPorTipo(tipoId) > 0)
            throw error(TIPO_HABITACION_EN_USO, "El tipo posee habitaciones activas y no puede darse de baja");
        tipo.darDeBaja();
        repositorio.guardar(tipo);
    }

    @Override
    public HabitacionResponse crearHabitacion(Long hotelId, HabitacionRequest s) {
        Hotel hotel = hotelActivo(hotelId);
        validarHabitacion(s);
        TipoHabitacion tipo = tipoDelHotel(hotelId, s.tipoHabitacionId());
        if (!tipo.isActivo()) throw error(TIPO_HABITACION_NO_ENCONTRADO, "El tipo de habitación está inactivo");
        String numero = limpiar(s.numero());
        if (repositorio.buscarHabitacionPorNumero(hotelId, numero).isPresent())
            throw error(NUMERO_HABITACION_DUPLICADO, "Ya existe la habitación " + numero + " en el hotel");
        return HotelMapper.habitacion(repositorio.guardar(new Habitacion(hotel, tipo, numero, s.piso(),
                normalizar(s.caracteristicas()))));
    }

    @Override
    public HabitacionResponse modificarHabitacion(Long hotelId, Long habitacionId, HabitacionRequest s) {
        hotelActivo(hotelId);
        validarHabitacion(s);
        Habitacion habitacion = habitacionDelHotel(hotelId, habitacionId);
        TipoHabitacion tipo = tipoDelHotel(hotelId, s.tipoHabitacionId());
        if (!tipo.isActivo()) throw error(TIPO_HABITACION_NO_ENCONTRADO, "El tipo de habitación está inactivo");
        String numero = limpiar(s.numero());
        repositorio.buscarHabitacionPorNumero(hotelId, numero)
                .filter(otra -> !otra.getId().equals(habitacionId))
                .ifPresent(otra -> { throw error(NUMERO_HABITACION_DUPLICADO, "Ya existe la habitación " + numero); });
        habitacion.actualizar(tipo, numero, s.piso(), normalizar(s.caracteristicas()));
        return HotelMapper.habitacion(repositorio.guardar(habitacion));
    }

    @Override
    public void darDeBajaHabitacion(Long hotelId, Long habitacionId) {
        Habitacion habitacion = habitacionDelHotel(hotelId, habitacionId);
        habitacion.darDeBaja();
        repositorio.guardar(habitacion);
    }

    @Override public boolean existeHotelActivo(Long hotelId) {
        return hotelId != null && repositorio.buscarHotel(hotelId).map(Hotel::isActivo).orElse(false);
    }
    @Override public boolean existeTipoHabitacionActivo(Long hotelId, String codigo) {
        return hotelId != null && codigo != null && repositorio.buscarTipoPorCodigo(hotelId, codigo.trim())
                .filter(TipoHabitacion::isActivo).filter(t -> t.getHotel().isActivo()).isPresent();
    }
    @Override public boolean existeHabitacionActiva(Long hotelId, Long habitacionId) {
        return hotelId != null && habitacionId != null && repositorio.buscarHabitacion(habitacionId)
                .filter(Habitacion::isActiva).filter(h -> h.getHotel().isActivo())
                .filter(h -> h.getHotel().getId().equals(hotelId)).isPresent();
    }
    @Override public int consultarCapacidadTipo(Long hotelId, String codigo) {
        if (codigo == null) throw error(SOLICITUD_INVALIDA, "El código es obligatorio");
        TipoHabitacion tipo = repositorio.buscarTipoPorCodigo(hotelId, codigo.trim())
                .filter(TipoHabitacion::isActivo)
                .filter(t -> t.getHotel().isActivo())
                .orElseThrow(() -> error(TIPO_HABITACION_NO_ENCONTRADO, "No existe el tipo " + codigo));
        return tipo.getCapacidadMaxima();
    }

    private HotelResponse respuesta(Hotel h) {
        return HotelMapper.hotel(h, repositorio.listarTipos(h.getId()), repositorio.listarHabitaciones(h.getId()));
    }
    private Hotel hotel(Long id) {
        if (id == null) throw error(SOLICITUD_INVALIDA, "El id del hotel es obligatorio");
        return repositorio.buscarHotel(id).orElseThrow(() -> error(HOTEL_NO_ENCONTRADO, "No existe el hotel " + id));
    }
    private Hotel hotelActivo(Long id) {
        Hotel hotel = hotel(id);
        if (!hotel.isActivo()) throw error(HOTEL_INACTIVO, "El hotel " + id + " está dado de baja");
        return hotel;
    }
    private TipoHabitacion tipoDelHotel(Long hotelId, Long tipoId) {
        if (tipoId == null) throw error(SOLICITUD_INVALIDA, "El id del tipo es obligatorio");
        return repositorio.buscarTipo(tipoId).filter(t -> t.getHotel().getId().equals(hotelId))
                .orElseThrow(() -> error(TIPO_HABITACION_NO_ENCONTRADO, "No existe el tipo " + tipoId + " en el hotel"));
    }
    private Habitacion habitacionDelHotel(Long hotelId, Long habitacionId) {
        if (habitacionId == null) throw error(SOLICITUD_INVALIDA, "El id de la habitación es obligatorio");
        return repositorio.buscarHabitacion(habitacionId).filter(h -> h.getHotel().getId().equals(hotelId))
                .orElseThrow(() -> error(HABITACION_NO_ENCONTRADA, "No existe la habitación " + habitacionId + " en el hotel"));
    }
    private void validarHotel(HotelRequest s) {
        if (s == null || vacio(s.nombre()) || vacio(s.direccion()) || vacio(s.ciudad()) || vacio(s.pais()))
            throw error(SOLICITUD_INVALIDA, "Nombre, dirección, ciudad y país son obligatorios");
    }
    private void validarTipo(TipoHabitacionRequest s) {
        if (s == null || vacio(s.codigo()) || vacio(s.nombre()) || s.capacidadMaxima() < 1)
            throw error(SOLICITUD_INVALIDA, "Código, nombre y capacidad máxima positiva son obligatorios");
    }
    private void validarHabitacion(HabitacionRequest s) {
        if (s == null || s.tipoHabitacionId() == null || vacio(s.numero()))
            throw error(SOLICITUD_INVALIDA, "Tipo de habitación y número son obligatorios");
    }
    private static HotelException error(CodigoErrorHotel codigo, String mensaje) { return new HotelException(codigo, mensaje); }
    private static boolean vacio(String valor) { return valor == null || valor.isBlank(); }
    private static String limpiar(String valor) { return valor.trim(); }
    private static String limpiarOpcional(String valor) { return valor == null ? null : valor.trim(); }
    private static String codigo(String valor) { return limpiar(valor).toUpperCase(Locale.ROOT); }
    private static Set<String> normalizar(Set<String> valores) {
        if (valores == null) return Set.of();
        Set<String> resultado = new LinkedHashSet<>();
        valores.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).forEach(resultado::add);
        return resultado;
    }
}
