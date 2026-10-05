package com.stayhub.hoteles.service;

import com.stayhub.hoteles.dto.HabitacionRequest;
import com.stayhub.hoteles.dto.HotelRequest;
import com.stayhub.hoteles.dto.TipoHabitacionRequest;
import com.stayhub.hoteles.exception.CodigoErrorHotel;
import com.stayhub.hoteles.exception.HotelException;
import com.stayhub.hoteles.model.Habitacion;
import com.stayhub.hoteles.model.Hotel;
import com.stayhub.hoteles.model.TipoHabitacion;
import com.stayhub.hoteles.repository.HotelRepository;
import jakarta.ejb.ApplicationException;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ServicioDeHotelesImplTest {
    private RepositorioStub repositorio;
    private ServicioDeHotelesImpl servicio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioStub();
        servicio = new ServicioDeHotelesImpl(repositorio);
    }

    @Test
    void normalizaElNumeroAntesDePersistir() {
        Hotel hotel = hotelActivo(1L);
        tipoActivo(2L, hotel, "DOBLE");

        var respuesta = servicio.crearHabitacion(1L,
                new HabitacionRequest(2L, "  Case-101  ", 1, Set.of("Vista")));

        assertEquals("CASE-101", respuesta.numero());
        assertEquals("CASE-101", repositorio.ultimaHabitacion.getNumero());
        assertEquals(1, repositorio.sincronizaciones);
    }

    @Test
    void traduceLaCarreraDeCodigoDuplicadoAConflictoDeDominio() {
        hotelActivo(1L);
        repositorio.errorAlSincronizar = violacionUnicaPostgres();

        HotelException error = assertThrows(HotelException.class, () -> servicio.crearTipo(1L,
                tipoRequest("DOBLE", new BigDecimal("2"))));

        assertEquals(CodigoErrorHotel.CODIGO_DUPLICADO, error.getCodigo());
        assertFalse(error.getMessage().contains("SQL"));
    }

    @Test
    void traduceLaCarreraDeNumeroDuplicadoAConflictoDeDominio() {
        Hotel hotel = hotelActivo(1L);
        tipoActivo(2L, hotel, "DOBLE");
        repositorio.errorAlSincronizar = violacionUnicaPostgres();

        HotelException error = assertThrows(HotelException.class, () -> servicio.crearHabitacion(1L,
                new HabitacionRequest(2L, "101", 1, Set.of())));

        assertEquals(CodigoErrorHotel.NUMERO_HABITACION_DUPLICADO, error.getCodigo());
        assertFalse(error.getMessage().contains("SQL"));
    }

    @Test
    void noDisfrazaOtrosErroresDePersistenciaComoDuplicados() {
        hotelActivo(1L);
        PersistenceException original = new PersistenceException(new SQLException("sin conexión", "08006"));
        repositorio.errorAlSincronizar = original;

        PersistenceException recibido = assertThrows(PersistenceException.class,
                () -> servicio.crearTipo(1L, tipoRequest("DOBLE", new BigDecimal("2"))));

        assertSame(original, recibido);
    }

    @Test
    void rechazaCapacidadConParteDecimal() {
        hotelActivo(1L);

        HotelException error = assertThrows(HotelException.class,
                () -> servicio.crearTipo(1L, tipoRequest("DOBLE", new BigDecimal("1.5"))));

        assertEquals(CodigoErrorHotel.SOLICITUD_INVALIDA, error.getCodigo());
    }

    @Test
    void noPermiteModificarHotelInactivo() {
        Hotel hotel = hotelActivo(1L);
        hotel.darDeBaja();

        HotelException error = assertThrows(HotelException.class, () -> servicio.modificarHotel(1L,
                new HotelRequest("Nuevo", "Calle 1", "Ciudad", "País", null, Set.of())));

        assertEquals(CodigoErrorHotel.HOTEL_INACTIVO, error.getCodigo());
    }

    @Test
    void noPermiteModificarTipoInactivo() {
        Hotel hotel = hotelActivo(1L);
        TipoHabitacion tipo = tipoActivo(2L, hotel, "DOBLE");
        tipo.darDeBaja();

        HotelException error = assertThrows(HotelException.class,
                () -> servicio.modificarTipo(1L, 2L, tipoRequest("TRIPLE", new BigDecimal("3"))));

        assertEquals(CodigoErrorHotel.TIPO_HABITACION_INACTIVO, error.getCodigo());
    }

    @Test
    void noPermiteModificarHabitacionInactiva() {
        Hotel hotel = hotelActivo(1L);
        TipoHabitacion tipo = tipoActivo(2L, hotel, "DOBLE");
        Habitacion habitacion = conId(new Habitacion(hotel, tipo, "101", 1, Set.of()), 3L);
        habitacion.darDeBaja();
        repositorio.habitaciones.put(3L, habitacion);

        HotelException error = assertThrows(HotelException.class, () -> servicio.modificarHabitacion(1L, 3L,
                new HabitacionRequest(2L, "102", 1, Set.of())));

        assertEquals(CodigoErrorHotel.HABITACION_INACTIVA, error.getCodigo());
        assertEquals("101", habitacion.getNumero());
    }

    @Test
    void lasExcepcionesDeNegocioSonExcepcionesDeAplicacionConRollback() {
        ApplicationException configuracion = HotelException.class.getAnnotation(ApplicationException.class);

        assertNotNull(configuracion);
        assertTrue(configuracion.rollback());
    }

    private Hotel hotelActivo(Long id) {
        Hotel hotel = conId(new Hotel("Hotel", "Calle 1", "Ciudad", "País", null, Set.of()), id);
        repositorio.hoteles.put(id, hotel);
        return hotel;
    }

    private TipoHabitacion tipoActivo(Long id, Hotel hotel, String codigo) {
        TipoHabitacion tipo = conId(new TipoHabitacion(hotel, codigo, "Habitación", null, 2, Set.of()), id);
        repositorio.tipos.put(id, tipo);
        return tipo;
    }

    private static TipoHabitacionRequest tipoRequest(String codigo, BigDecimal capacidad) {
        return new TipoHabitacionRequest(codigo, "Habitación", null, capacidad, Set.of());
    }

    private static PersistenceException violacionUnicaPostgres() {
        return new PersistenceException(new SQLException("detalle SQL que no debe salir", "23505"));
    }

    private static <T> T conId(T entidad, Long id) {
        try {
            Field campo = entidad.getClass().getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entidad, id);
            return entidad;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static class RepositorioStub implements HotelRepository {
        private final Map<Long, Hotel> hoteles = new LinkedHashMap<>();
        private final Map<Long, TipoHabitacion> tipos = new LinkedHashMap<>();
        private final Map<Long, Habitacion> habitaciones = new LinkedHashMap<>();
        private Habitacion ultimaHabitacion;
        private RuntimeException errorAlSincronizar;
        private int sincronizaciones;

        @Override public Hotel guardar(Hotel hotel) {
            if (hotel.getId() != null) hoteles.put(hotel.getId(), hotel);
            return hotel;
        }
        @Override public TipoHabitacion guardar(TipoHabitacion tipo) {
            if (tipo.getId() != null) tipos.put(tipo.getId(), tipo);
            return tipo;
        }
        @Override public Habitacion guardar(Habitacion habitacion) {
            ultimaHabitacion = habitacion;
            if (habitacion.getId() != null) habitaciones.put(habitacion.getId(), habitacion);
            return habitacion;
        }
        @Override public void sincronizar() {
            sincronizaciones++;
            if (errorAlSincronizar != null) throw errorAlSincronizar;
        }
        @Override public Optional<Hotel> buscarHotel(Long id) { return Optional.ofNullable(hoteles.get(id)); }
        @Override public Optional<TipoHabitacion> buscarTipo(Long id) { return Optional.ofNullable(tipos.get(id)); }
        @Override public Optional<TipoHabitacion> buscarTipoPorCodigo(Long hotelId, String codigo) {
            return tipos.values().stream()
                    .filter(t -> t.getHotel().getId().equals(hotelId))
                    .filter(t -> t.getCodigo().equalsIgnoreCase(codigo))
                    .findFirst();
        }
        @Override public Optional<Habitacion> buscarHabitacion(Long id) {
            return Optional.ofNullable(habitaciones.get(id));
        }
        @Override public Optional<Habitacion> buscarHabitacionPorNumero(Long hotelId, String numero) {
            return habitaciones.values().stream()
                    .filter(h -> h.getHotel().getId().equals(hotelId))
                    .filter(h -> h.getNumero().equalsIgnoreCase(numero))
                    .findFirst();
        }
        @Override public List<Hotel> listarHoteles(boolean incluirInactivos) {
            return hoteles.values().stream().filter(h -> incluirInactivos || h.isActivo()).toList();
        }
        @Override public List<TipoHabitacion> listarTipos(Long hotelId) {
            return tipos.values().stream().filter(t -> t.getHotel().getId().equals(hotelId)).toList();
        }
        @Override public List<Habitacion> listarHabitaciones(Long hotelId) {
            return habitaciones.values().stream().filter(h -> h.getHotel().getId().equals(hotelId)).toList();
        }
        @Override public long contarHabitacionesActivasPorTipo(Long tipoId) {
            return new ArrayList<>(habitaciones.values()).stream()
                    .filter(Habitacion::isActiva)
                    .filter(h -> h.getTipo().getId().equals(tipoId))
                    .count();
        }
    }
}
