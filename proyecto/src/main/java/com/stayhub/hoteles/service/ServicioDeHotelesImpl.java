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

/**
 * Implementación stateless de la Facade de ServicioDeHoteles.
 *
 * <p>Cada operación recibe todos los datos que necesita y conserva el estado de negocio en
 * PostgreSQL mediante el DAO. Por eso no existe estado conversacional asociado a una instancia
 * del componente y WildFly puede administrar libremente el pool de EJBs.</p>
 */
@Stateless
public class ServicioDeHotelesImpl implements ServicioDeHoteles, ServicioDeHotelesPort {
    private static final int MAX_NOMBRE_HOTEL = 120;
    private static final int MAX_DIRECCION = 180;
    private static final int MAX_CIUDAD_PAIS = 100;
    private static final int MAX_DESCRIPCION_HOTEL = 1000;
    private static final int MAX_SERVICIO = 80;
    private static final int MAX_CODIGO_TIPO = 30;
    private static final int MAX_NOMBRE_TIPO = 100;
    private static final int MAX_DESCRIPCION_TIPO = 800;
    private static final int MAX_NUMERO_HABITACION = 30;
    private static final int MAX_CARACTERISTICA = 100;

    @Inject
    private HotelRepository repositorio;

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
    public TipoHabitacionResponse consultarTipo(Long hotelId, Long tipoId) {
        hotel(hotelId);
        return HotelMapper.tipo(tipoDelHotel(hotelId, tipoId));
    }

    @Override
    public List<TipoHabitacionResponse> listarTipos(Long hotelId, boolean incluirInactivos) {
        hotel(hotelId);
        return repositorio.listarTipos(hotelId).stream()
                .filter(tipo -> incluirInactivos || tipo.isActivo())
                .map(HotelMapper::tipo)
                .toList();
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
    public HabitacionResponse consultarHabitacion(Long hotelId, Long habitacionId) {
        hotel(hotelId);
        return HotelMapper.habitacion(habitacionDelHotel(hotelId, habitacionId));
    }

    @Override
    public List<HabitacionResponse> listarHabitaciones(Long hotelId, boolean incluirInactivas) {
        hotel(hotelId);
        return repositorio.listarHabitaciones(hotelId).stream()
                .filter(habitacion -> incluirInactivas || habitacion.isActiva())
                .map(HotelMapper::habitacion)
                .toList();
    }

    @Override
    public void darDeBajaHabitacion(Long hotelId, Long habitacionId) {
        Habitacion habitacion = habitacionDelHotel(hotelId, habitacionId);
        habitacion.darDeBaja();
        repositorio.guardar(habitacion);
    }

    @Override public boolean existeHotelActivo(Long hotelId) {
        return esIdValido(hotelId) && repositorio.buscarHotel(hotelId).map(Hotel::isActivo).orElse(false);
    }
    @Override public boolean existeTipoHabitacionActivo(Long hotelId, String codigo) {
        return esIdValido(hotelId) && !vacio(codigo) && repositorio.buscarTipoPorCodigo(hotelId, codigo.trim())
                .filter(TipoHabitacion::isActivo).filter(t -> t.getHotel().isActivo()).isPresent();
    }
    @Override public boolean existeHabitacionActiva(Long hotelId, Long habitacionId) {
        return esIdValido(hotelId) && esIdValido(habitacionId) && repositorio.buscarHabitacion(habitacionId)
                .filter(Habitacion::isActiva).filter(h -> h.getHotel().isActivo())
                .filter(h -> h.getHotel().getId().equals(hotelId)).isPresent();
    }
    @Override public int consultarCapacidadTipo(Long hotelId, String codigo) {
        validarId(hotelId, "hotel");
        if (vacio(codigo)) throw error(SOLICITUD_INVALIDA, "El código es obligatorio");
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
        validarId(id, "hotel");
        return repositorio.buscarHotel(id).orElseThrow(() -> error(HOTEL_NO_ENCONTRADO, "No existe el hotel " + id));
    }
    private Hotel hotelActivo(Long id) {
        Hotel hotel = hotel(id);
        if (!hotel.isActivo()) throw error(HOTEL_INACTIVO, "El hotel " + id + " está dado de baja");
        return hotel;
    }
    private TipoHabitacion tipoDelHotel(Long hotelId, Long tipoId) {
        validarId(hotelId, "hotel");
        validarId(tipoId, "tipo de habitación");
        return repositorio.buscarTipo(tipoId).filter(t -> t.getHotel().getId().equals(hotelId))
                .orElseThrow(() -> error(TIPO_HABITACION_NO_ENCONTRADO, "No existe el tipo " + tipoId + " en el hotel"));
    }
    private Habitacion habitacionDelHotel(Long hotelId, Long habitacionId) {
        validarId(hotelId, "hotel");
        validarId(habitacionId, "habitación");
        return repositorio.buscarHabitacion(habitacionId).filter(h -> h.getHotel().getId().equals(hotelId))
                .orElseThrow(() -> error(HABITACION_NO_ENCONTRADA, "No existe la habitación " + habitacionId + " en el hotel"));
    }
    private void validarHotel(HotelRequest s) {
        if (s == null || vacio(s.nombre()) || vacio(s.direccion()) || vacio(s.ciudad()) || vacio(s.pais()))
            throw error(SOLICITUD_INVALIDA, "Nombre, dirección, ciudad y país son obligatorios");
        validarLongitud("nombre", s.nombre(), MAX_NOMBRE_HOTEL);
        validarLongitud("dirección", s.direccion(), MAX_DIRECCION);
        validarLongitud("ciudad", s.ciudad(), MAX_CIUDAD_PAIS);
        validarLongitud("país", s.pais(), MAX_CIUDAD_PAIS);
        validarLongitud("descripción", s.descripcion(), MAX_DESCRIPCION_HOTEL);
        validarColeccion("servicio", s.servicios(), MAX_SERVICIO);
    }
    private void validarTipo(TipoHabitacionRequest s) {
        if (s == null || vacio(s.codigo()) || vacio(s.nombre()) || s.capacidadMaxima() < 1)
            throw error(SOLICITUD_INVALIDA, "Código, nombre y capacidad máxima positiva son obligatorios");
        validarLongitud("código", s.codigo(), MAX_CODIGO_TIPO);
        validarLongitud("nombre", s.nombre(), MAX_NOMBRE_TIPO);
        validarLongitud("descripción", s.descripcion(), MAX_DESCRIPCION_TIPO);
        validarColeccion("característica", s.caracteristicas(), MAX_CARACTERISTICA);
    }
    private void validarHabitacion(HabitacionRequest s) {
        if (s == null || s.tipoHabitacionId() == null || vacio(s.numero()))
            throw error(SOLICITUD_INVALIDA, "Tipo de habitación y número son obligatorios");
        validarId(s.tipoHabitacionId(), "tipo de habitación");
        validarLongitud("número", s.numero(), MAX_NUMERO_HABITACION);
        validarColeccion("característica", s.caracteristicas(), MAX_CARACTERISTICA);
    }

    private static void validarId(Long id, String campo) {
        if (!esIdValido(id))
            throw error(SOLICITUD_INVALIDA, "El id de " + campo + " debe ser un número positivo");
    }

    private static void validarLongitud(String campo, String valor, int maximo) {
        if (valor != null && valor.trim().length() > maximo)
            throw error(SOLICITUD_INVALIDA, "El campo " + campo + " admite hasta " + maximo + " caracteres");
    }

    private static void validarColeccion(String campo, Set<String> valores, int maximo) {
        if (valores == null) return;
        valores.stream()
                .filter(Objects::nonNull)
                .filter(valor -> valor.trim().length() > maximo)
                .findFirst()
                .ifPresent(valor -> {
                    throw error(SOLICITUD_INVALIDA,
                            "Cada " + campo + " admite hasta " + maximo + " caracteres");
                });
    }

    private static HotelException error(CodigoErrorHotel codigo, String mensaje) { return new HotelException(codigo, mensaje); }
    private static boolean esIdValido(Long id) { return id != null && id > 0; }
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
