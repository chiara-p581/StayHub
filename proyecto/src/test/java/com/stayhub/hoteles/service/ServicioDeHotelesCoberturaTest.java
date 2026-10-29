package com.stayhub.hoteles.service;

import com.stayhub.hoteles.dto.*;
import com.stayhub.hoteles.exception.CodigoErrorHotel;
import com.stayhub.hoteles.exception.HotelException;
import com.stayhub.hoteles.model.Habitacion;
import com.stayhub.hoteles.model.Hotel;
import com.stayhub.hoteles.model.TipoHabitacion;
import com.stayhub.hoteles.repository.HotelRepository;
import com.stayhub.seguridad.SeguridadException;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas de ServicioDeHotelesImpl con el repositorio como unico colaborador simulado. */
class ServicioDeHotelesCoberturaTest {
    private HotelRepository repo;
    private ServicioDeHotelesImpl servicio;
    private Hotel hotel;
    private TipoHabitacion tipo;

    @BeforeEach
    void preparar() {
        repo = mock(HotelRepository.class);
        servicio = new ServicioDeHotelesImpl(repo);
        hotel = conId(new Hotel("Hotel", "Calle 1", "Ciudad", "Pais", null, Set.of()), 1L);
        tipo = conId(new TipoHabitacion(hotel, "DOBLE", "Doble", null, 2, Set.of()), 2L);
        when(repo.buscarHotel(1L)).thenReturn(Optional.of(hotel));
        when(repo.buscarTipo(2L)).thenReturn(Optional.of(tipo));
    }

    private static <T> T conId(T e, Long id) {
        try {
            Field f = e.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
            return e;
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private Habitacion habitacion(Long id, String numero) {
        Habitacion h = conId(new Habitacion(hotel, tipo, numero, 1, Set.of("Vista")), id);
        when(repo.buscarHabitacion(id)).thenReturn(Optional.of(h));
        return h;
    }

    private static HotelRequest hotelReq(String n, String d, String c, String p, String desc, Set<String> s) {
        return new HotelRequest(n, d, c, p, desc, s);
    }

    private static TipoHabitacionRequest tipoReq(String cod, String nom, BigDecimal cap) {
        return new TipoHabitacionRequest(cod, nom, "desc", cap, Set.of("TV"));
    }

    private static HotelException assertHotel(CodigoErrorHotel codigo, Executable e) {
        HotelException ex = assertThrows(HotelException.class, e);
        assertEquals(codigo, ex.getCodigo());
        return ex;
    }

    // ---------- crearHotel / modificarHotel ----------

    @Test
    void crearHotelLimpiaEspaciosYNormalizaServicios() {
        Set<String> servicios = new LinkedHashSet<>(Arrays.asList(" Wifi ", null, "  ", "Pileta"));
        var r = servicio.crearHotel(hotelReq("  Gran Hotel ", " Av 1 ", " BA ", " AR ", "  lindo ", servicios));

        assertEquals("Gran Hotel", r.nombre());
        assertEquals("Av 1", r.direccion());
        assertEquals("BA", r.ciudad());
        assertEquals("AR", r.pais());
        assertEquals("lindo", r.descripcion());
        assertEquals(Set.of("Wifi", "Pileta"), r.servicios());
        assertTrue(r.activo());
        verify(repo).guardar(any(Hotel.class));
    }

    @Test
    void crearHotelSinDescripcionNiServiciosConservaNulosNormalizados() {
        var r = servicio.crearHotel(hotelReq("H", "D", "C", "P", null, null));

        assertNull(r.descripcion());
        assertTrue(r.servicios().isEmpty());
    }

    @Test
    void crearHotelAceptaLongitudesExactamenteEnElLimite() {
        var r = servicio.crearHotel(hotelReq("n".repeat(120), "d".repeat(180), "c".repeat(100),
                "p".repeat(100), "x".repeat(1000), Set.of("s".repeat(80))));

        assertEquals(120, r.nombre().length());
        assertEquals(1000, r.descripcion().length());
    }

    @Test
    void crearHotelNuloEsInvalidoYNoPersiste() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHotel(null));
        verify(repo, never()).guardar(any(Hotel.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void crearHotelRechazaCamposObligatoriosVaciosONulos(int campo) {
        String[] nulo = {"N", "D", "C", "P"};
        nulo[campo] = null;
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA,
                () -> servicio.crearHotel(hotelReq(nulo[0], nulo[1], nulo[2], nulo[3], null, null)));
        String[] blanco = {"N", "D", "C", "P"};
        blanco[campo] = "   ";
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA,
                () -> servicio.crearHotel(hotelReq(blanco[0], blanco[1], blanco[2], blanco[3], null, null)));
        verify(repo, never()).guardar(any(Hotel.class));
    }

    @Test
    void crearHotelRechazaCadaCampoQueExcedeSuLongitud() {
        var largo = "x";
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHotel(
                hotelReq(largo.repeat(121), "D", "C", "P", null, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHotel(
                hotelReq("N", largo.repeat(181), "C", "P", null, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHotel(
                hotelReq("N", "D", largo.repeat(101), "P", null, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHotel(
                hotelReq("N", "D", "C", largo.repeat(101), null, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHotel(
                hotelReq("N", "D", "C", "P", largo.repeat(1001), null)));
        var ex = assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHotel(
                hotelReq("N", "D", "C", "P", null, Set.of(largo.repeat(81)))));
        assertTrue(ex.getMessage().contains("servicio"));
        verify(repo, never()).guardar(any(Hotel.class));
    }

    @Test
    void modificarHotelActualizaDatosYReemplazaServicios() {
        hotel.actualizar("Hotel", "Calle 1", "Ciudad", "Pais", "vieja", Set.of("Wifi"));

        var r = servicio.modificarHotel(1L, hotelReq(" Nuevo ", "Av 9", "Rosario", "AR", null, Set.of("Spa")));

        assertEquals("Nuevo", r.nombre());
        assertEquals("Rosario", hotel.getCiudad());
        assertNull(hotel.getDescripcion());
        assertEquals(Set.of("Spa"), hotel.getServicios());
        verify(repo).guardar(hotel);
    }

    @Test
    void modificarHotelValidaLaSolicitudAntesDeBuscar() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA,
                () -> servicio.modificarHotel(1L, hotelReq("", "D", "C", "P", null, null)));
        verifyNoInteractions(repo);
    }

    @Test
    void modificarHotelInexistenteInformaNoEncontrado() {
        var ex = assertHotel(CodigoErrorHotel.HOTEL_NO_ENCONTRADO,
                () -> servicio.modificarHotel(99L, hotelReq("N", "D", "C", "P", null, null)));
        assertTrue(ex.getMessage().contains("99"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -5})
    void consultarHotelConIdNoPositivoEsInvalido(long id) {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.consultarHotel(id));
        verify(repo, never()).buscarHotel(anyLong());
    }

    @Test
    void consultarHotelConIdNuloEsInvalido() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.consultarHotel(null));
    }

    @Test
    void consultarHotelIncluyeTiposYHabitaciones() {
        Habitacion h = habitacion(3L, "101");
        when(repo.listarTipos(1L)).thenReturn(List.of(tipo));
        when(repo.listarHabitaciones(1L)).thenReturn(List.of(h));

        var r = servicio.consultarHotel(1L);

        assertEquals(1L, r.id());
        assertEquals(1, r.tiposHabitacion().size());
        assertEquals("DOBLE", r.tiposHabitacion().get(0).codigo());
        assertEquals("101", r.habitaciones().get(0).numero());
        assertEquals(2, r.habitaciones().get(0).capacidadMaxima());
        assertEquals("DOBLE", r.habitaciones().get(0).tipoHabitacionCodigo());
        assertEquals(Set.of("Vista"), r.habitaciones().get(0).caracteristicas());
    }

    @Test
    void consultarHotelInexistente() {
        assertHotel(CodigoErrorHotel.HOTEL_NO_ENCONTRADO, () -> servicio.consultarHotel(7L));
    }

    @Test
    void listarHotelesNoCargaTiposNiHabitaciones() {
        when(repo.listarHoteles(true)).thenReturn(List.of(hotel));

        var r = servicio.listarHoteles(true);

        assertEquals(1, r.size());
        assertTrue(r.get(0).tiposHabitacion().isEmpty());
        assertTrue(r.get(0).habitaciones().isEmpty());
        verify(repo, never()).listarTipos(anyLong());
    }

    @Test
    void listarHotelesPaginadoDelegaOffsetYLimite() {
        when(repo.listarHoteles(false, 5, 20)).thenReturn(List.of(hotel));

        var r = servicio.listarHoteles(false, 5, 20);

        assertEquals("Hotel", r.get(0).nombre());
        verify(repo).listarHoteles(false, 5, 20);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -1})
    void listarHotelesPaginadoRechazaLimiteFueraDeRango(int limite) {
        assertThrows(SeguridadException.class, () -> servicio.listarHoteles(false, 0, limite));
        verifyNoInteractions(repo);
    }

    @Test
    void listarHotelesPaginadoRechazaOffsetNegativoYAcepta10000() {
        assertThrows(SeguridadException.class, () -> servicio.listarHoteles(false, -1, 10));
        assertThrows(SeguridadException.class, () -> servicio.listarHoteles(false, 10001, 10));
        when(repo.listarHoteles(false, 10000, 100)).thenReturn(List.of());
        assertTrue(servicio.listarHoteles(false, 10000, 100).isEmpty());
    }

    @Test
    void darDeBajaHotelDesactivaYBajaDependientes() {
        var r = servicio.darDeBajaHotel(1L);

        assertFalse(r.activo());
        assertFalse(hotel.isActivo());
        verify(repo).darDeBajaDependientes(1L);
        verify(repo).guardar(hotel);
    }

    // ---------- tipos ----------

    @Test
    void crearTipoNormalizaCodigoAMayusculas() {
        when(repo.buscarTipoPorCodigo(1L, "SUITE")).thenReturn(Optional.empty());
        when(repo.guardar(any(TipoHabitacion.class))).thenAnswer(i -> i.getArgument(0));

        var r = servicio.crearTipo(1L, new TipoHabitacionRequest(" suite ", " Suite ", " d ",
                new BigDecimal("4"), new LinkedHashSet<>(Arrays.asList("TV", " ", null))));

        assertEquals("SUITE", r.codigo());
        assertEquals("Suite", r.nombre());
        assertEquals("d", r.descripcion());
        assertEquals(4, r.capacidadMaxima());
        assertTrue(r.activo());
        assertEquals(Set.of("TV"), r.caracteristicas());
        verify(repo).sincronizar();
    }

    @Test
    void crearTipoConCodigoExistenteLanzaDuplicadoSinGuardar() {
        when(repo.buscarTipoPorCodigo(1L, "DOBLE")).thenReturn(Optional.of(tipo));

        var ex = assertHotel(CodigoErrorHotel.CODIGO_DUPLICADO,
                () -> servicio.crearTipo(1L, tipoReq("doble", "N", BigDecimal.TEN)));

        assertTrue(ex.getMessage().contains("DOBLE"));
        verify(repo, never()).guardar(any(TipoHabitacion.class));
    }

    @Test
    void crearTipoEnHotelInactivoFalla() {
        hotel.darDeBaja();
        assertHotel(CodigoErrorHotel.HOTEL_INACTIVO,
                () -> servicio.crearTipo(1L, tipoReq("X", "N", BigDecimal.ONE)));
    }

    @Test
    void crearTipoEnHotelInexistenteFalla() {
        assertHotel(CodigoErrorHotel.HOTEL_NO_ENCONTRADO,
                () -> servicio.crearTipo(50L, tipoReq("X", "N", BigDecimal.ONE)));
    }

    @Test
    void crearTipoRechazaSolicitudesInvalidas() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, null));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq(null, "N", BigDecimal.ONE)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq(" ", "N", BigDecimal.ONE)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq("C", null, BigDecimal.ONE)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq("C", " ", BigDecimal.ONE)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq("C", "N", null)));
        verify(repo, never()).guardar(any(TipoHabitacion.class));
    }

    @Test
    void crearTipoRechazaCapacidadCeroNegativaOEnormeYAceptaUno() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq("C", "N", BigDecimal.ZERO)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq("C", "N", new BigDecimal("-1"))));
        var enorme = assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA,
                () -> servicio.crearTipo(1L, tipoReq("C", "N", new BigDecimal("99999999999"))));
        assertTrue(enorme.getMessage().contains("entero"));
        when(repo.guardar(any(TipoHabitacion.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(1, servicio.crearTipo(1L, tipoReq("C", "N", BigDecimal.ONE)).capacidadMaxima());
        assertEquals(2, servicio.crearTipo(1L, tipoReq("D", "N", new BigDecimal("2.0"))).capacidadMaxima());
    }

    @Test
    void crearTipoRechazaLongitudesExcedidas() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq("c".repeat(31), "N", BigDecimal.ONE)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L, tipoReq("C", "n".repeat(101), BigDecimal.ONE)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L,
                new TipoHabitacionRequest("C", "N", "d".repeat(801), BigDecimal.ONE, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearTipo(1L,
                new TipoHabitacionRequest("C", "N", null, BigDecimal.ONE, Set.of("x".repeat(101)))));
    }

    @Test
    void crearTipoPropagaCarreraDeUnicidadComoDuplicado() {
        when(repo.guardar(any(TipoHabitacion.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new PersistenceException(new SQLException("x", "23505"))).when(repo).sincronizar();

        assertHotel(CodigoErrorHotel.CODIGO_DUPLICADO,
                () -> servicio.crearTipo(1L, tipoReq("NUEVO", "N", BigDecimal.ONE)));
    }

    @Test
    void modificarTipoActualizaCamposYPermiteConservarSuPropioCodigo() {
        when(repo.buscarTipoPorCodigo(1L, "DOBLE")).thenReturn(Optional.of(tipo));
        when(repo.guardar(tipo)).thenReturn(tipo);

        var r = servicio.modificarTipo(1L, 2L, new TipoHabitacionRequest("doble", "Doble Plus", null,
                new BigDecimal("3"), Set.of("Balcon")));

        assertEquals("Doble Plus", r.nombre());
        assertEquals(3, tipo.getCapacidadMaxima());
        assertEquals(Set.of("Balcon"), tipo.getCaracteristicas());
        verify(repo).sincronizar();
    }

    @Test
    void modificarTipoConCodigoDeOtroTipoEsDuplicado() {
        TipoHabitacion otro = conId(new TipoHabitacion(hotel, "SUITE", "S", null, 4, Set.of()), 9L);
        when(repo.buscarTipoPorCodigo(1L, "SUITE")).thenReturn(Optional.of(otro));

        assertHotel(CodigoErrorHotel.CODIGO_DUPLICADO,
                () -> servicio.modificarTipo(1L, 2L, tipoReq("suite", "N", BigDecimal.ONE)));

        assertEquals("DOBLE", tipo.getCodigo());
        verify(repo, never()).guardar(any(TipoHabitacion.class));
    }

    @Test
    void modificarTipoDeOtroHotelNoSeEncuentra() {
        Hotel otroHotel = conId(new Hotel("O", "D", "C", "P", null, null), 5L);
        when(repo.buscarHotel(5L)).thenReturn(Optional.of(otroHotel));

        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO,
                () -> servicio.modificarTipo(5L, 2L, tipoReq("C", "N", BigDecimal.ONE)));
    }

    @Test
    void modificarTipoConIdInvalidoEsSolicitudInvalida() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA,
                () -> servicio.modificarTipo(1L, 0L, tipoReq("C", "N", BigDecimal.ONE)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA,
                () -> servicio.modificarTipo(1L, null, tipoReq("C", "N", BigDecimal.ONE)));
    }

    @Test
    void modificarTipoInactivoFalla() {
        tipo.darDeBaja();
        assertHotel(CodigoErrorHotel.TIPO_HABITACION_INACTIVO,
                () -> servicio.modificarTipo(1L, 2L, tipoReq("C", "N", BigDecimal.ONE)));
    }

    @Test
    void consultarTipoDevuelveDatosDelTipo() {
        var r = servicio.consultarTipo(1L, 2L);
        assertEquals(2L, r.id());
        assertEquals("DOBLE", r.codigo());
        assertEquals(2, r.capacidadMaxima());
    }

    @Test
    void consultarTipoDeOtroHotelFalla() {
        Hotel otro = conId(new Hotel("O", "D", "C", "P", null, null), 5L);
        when(repo.buscarHotel(5L)).thenReturn(Optional.of(otro));
        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO, () -> servicio.consultarTipo(5L, 2L));
    }

    @Test
    void listarTiposOcultaInactivosSalvoQueSePidan() {
        TipoHabitacion baja = conId(new TipoHabitacion(hotel, "VIEJO", "V", null, 1, Set.of()), 8L);
        baja.darDeBaja();
        when(repo.listarTipos(1L)).thenReturn(List.of(tipo, baja));

        assertEquals(List.of("DOBLE"), servicio.listarTipos(1L, false).stream().map(TipoHabitacionResponse::codigo).toList());
        assertEquals(2, servicio.listarTipos(1L, true).size());
    }

    @Test
    void listarTiposPaginadoUsaElLimiteValidado() {
        when(repo.listarTipos(1L, true, 0, 10)).thenReturn(List.of(tipo));
        assertEquals(1, servicio.listarTipos(1L, true, 0, 10).size());
        assertThrows(SeguridadException.class, () -> servicio.listarTipos(1L, true, 0, 0));
        assertHotel(CodigoErrorHotel.HOTEL_NO_ENCONTRADO, () -> servicio.listarTipos(66L, true, 0, 10));
    }

    @Test
    void darDeBajaTipoSinHabitacionesActivasLoDesactiva() {
        when(repo.contarHabitacionesActivasPorTipo(2L)).thenReturn(0L);

        servicio.darDeBajaTipo(1L, 2L);

        assertFalse(tipo.isActivo());
        verify(repo).guardar(tipo);
    }

    @Test
    void darDeBajaTipoConHabitacionesActivasEstaEnUso() {
        when(repo.contarHabitacionesActivasPorTipo(2L)).thenReturn(1L);

        assertHotel(CodigoErrorHotel.TIPO_HABITACION_EN_USO, () -> servicio.darDeBajaTipo(1L, 2L));

        assertTrue(tipo.isActivo());
        verify(repo, never()).guardar(any(TipoHabitacion.class));
    }

    // ---------- habitaciones ----------

    @Test
    void crearHabitacionRechazaTipoInactivo() {
        tipo.darDeBaja();
        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO,
                () -> servicio.crearHabitacion(1L, new HabitacionRequest(2L, "101", 1, null)));
        verify(repo, never()).guardar(any(Habitacion.class));
    }

    @Test
    void crearHabitacionConNumeroExistenteEsDuplicada() {
        Habitacion existente = habitacion(3L, "101");
        when(repo.buscarHabitacionPorNumero(1L, "101")).thenReturn(Optional.of(existente));

        var ex = assertHotel(CodigoErrorHotel.NUMERO_HABITACION_DUPLICADO,
                () -> servicio.crearHabitacion(1L, new HabitacionRequest(2L, " 101 ", 1, null)));

        assertTrue(ex.getMessage().contains("101"));
        verify(repo, never()).guardar(any(Habitacion.class));
    }

    @Test
    void crearHabitacionExitosaDevuelveDatosDelTipo() {
        when(repo.guardar(any(Habitacion.class))).thenAnswer(i -> i.getArgument(0));

        var r = servicio.crearHabitacion(1L, new HabitacionRequest(2L, "a-1", 3, Set.of("Vista", " ")));

        assertEquals("A-1", r.numero());
        assertEquals(3, r.piso());
        assertTrue(r.activa());
        assertEquals(2L, r.tipoHabitacionId());
        assertEquals("DOBLE", r.tipoHabitacionCodigo());
        assertEquals(Set.of("Vista"), r.caracteristicas());
        verify(repo).sincronizar();
    }

    @Test
    void crearHabitacionRechazaSolicitudesInvalidas() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHabitacion(1L, null));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHabitacion(1L, new HabitacionRequest(null, "1", 1, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHabitacion(1L, new HabitacionRequest(2L, " ", 1, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHabitacion(1L, new HabitacionRequest(-2L, "1", 1, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHabitacion(1L, new HabitacionRequest(2L, "n".repeat(31), 1, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.crearHabitacion(1L, new HabitacionRequest(2L, "1", 1, Set.of("c".repeat(101)))));
        verify(repo, never()).guardar(any(Habitacion.class));
    }

    @Test
    void crearHabitacionEnHotelInactivoFalla() {
        hotel.darDeBaja();
        assertHotel(CodigoErrorHotel.HOTEL_INACTIVO,
                () -> servicio.crearHabitacion(1L, new HabitacionRequest(2L, "1", 1, null)));
    }

    @Test
    void crearHabitacionConTipoInexistenteFalla() {
        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO,
                () -> servicio.crearHabitacion(1L, new HabitacionRequest(77L, "1", 1, null)));
    }

    @Test
    void modificarHabitacionActualizaNumeroPisoYCaracteristicas() {
        Habitacion h = habitacion(3L, "101");
        when(repo.buscarHabitacionPorNumero(1L, "102")).thenReturn(Optional.empty());
        when(repo.guardar(h)).thenReturn(h);

        var r = servicio.modificarHabitacion(1L, 3L, new HabitacionRequest(2L, "102", 4, Set.of("Balcon")));

        assertEquals("102", r.numero());
        assertEquals(4, h.getPiso());
        assertEquals(Set.of("Balcon"), h.getCaracteristicas());
    }

    @Test
    void modificarHabitacionPuedeConservarSuPropioNumero() {
        Habitacion h = habitacion(3L, "101");
        when(repo.buscarHabitacionPorNumero(1L, "101")).thenReturn(Optional.of(h));
        when(repo.guardar(h)).thenReturn(h);

        assertEquals("101", servicio.modificarHabitacion(1L, 3L, new HabitacionRequest(2L, "101", 2, null)).numero());
    }

    @Test
    void modificarHabitacionConNumeroDeOtraEsDuplicada() {
        Habitacion h = habitacion(3L, "101");
        Habitacion otra = habitacion(4L, "102");
        when(repo.buscarHabitacionPorNumero(1L, "102")).thenReturn(Optional.of(otra));

        assertHotel(CodigoErrorHotel.NUMERO_HABITACION_DUPLICADO,
                () -> servicio.modificarHabitacion(1L, 3L, new HabitacionRequest(2L, "102", 1, null)));

        assertEquals("101", h.getNumero());
        verify(repo, never()).guardar(any(Habitacion.class));
    }

    @Test
    void modificarHabitacionConTipoInactivoFalla() {
        habitacion(3L, "101");
        TipoHabitacion baja = conId(new TipoHabitacion(hotel, "OLD", "O", null, 1, Set.of()), 6L);
        baja.darDeBaja();
        when(repo.buscarTipo(6L)).thenReturn(Optional.of(baja));

        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO,
                () -> servicio.modificarHabitacion(1L, 3L, new HabitacionRequest(6L, "105", 1, null)));
    }

    @Test
    void modificarHabitacionInexistenteOConIdInvalido() {
        assertHotel(CodigoErrorHotel.HABITACION_NO_ENCONTRADA,
                () -> servicio.modificarHabitacion(1L, 40L, new HabitacionRequest(2L, "1", 1, null)));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA,
                () -> servicio.modificarHabitacion(1L, 0L, new HabitacionRequest(2L, "1", 1, null)));
    }

    @Test
    void modificarHabitacionInactivaFalla() {
        Habitacion h = habitacion(3L, "101");
        h.darDeBaja();
        assertHotel(CodigoErrorHotel.HABITACION_INACTIVA,
                () -> servicio.modificarHabitacion(1L, 3L, new HabitacionRequest(2L, "102", 1, null)));
        assertEquals("101", h.getNumero());
    }

    @Test
    void consultarHabitacionDevuelveSusDatos() {
        habitacion(3L, "101");
        var r = servicio.consultarHabitacion(1L, 3L);
        assertEquals(3L, r.id());
        assertEquals("101", r.numero());
    }

    @Test
    void consultarHabitacionDeOtroHotelNoSeEncuentra() {
        Hotel otro = conId(new Hotel("O", "D", "C", "P", null, null), 5L);
        when(repo.buscarHotel(5L)).thenReturn(Optional.of(otro));
        habitacion(3L, "101");
        assertHotel(CodigoErrorHotel.HABITACION_NO_ENCONTRADA, () -> servicio.consultarHabitacion(5L, 3L));
    }

    @Test
    void listarHabitacionesFiltraInactivas() {
        Habitacion a = habitacion(3L, "101");
        Habitacion b = habitacion(4L, "102");
        b.darDeBaja();
        when(repo.listarHabitaciones(1L)).thenReturn(List.of(a, b));

        assertEquals(List.of("101"), servicio.listarHabitaciones(1L, false).stream().map(HabitacionResponse::numero).toList());
        assertEquals(2, servicio.listarHabitaciones(1L, true).size());
    }

    @Test
    void listarHabitacionesPaginado() {
        Habitacion a = habitacion(3L, "101");
        when(repo.listarHabitaciones(1L, false, 0, 50)).thenReturn(List.of(a));
        assertEquals("101", servicio.listarHabitaciones(1L, false, 0, 50).get(0).numero());
        assertThrows(SeguridadException.class, () -> servicio.listarHabitaciones(1L, false, 0, 101));
    }

    @Test
    void darDeBajaHabitacionLaDesactivaYGuarda() {
        Habitacion h = habitacion(3L, "101");
        servicio.darDeBajaHabitacion(1L, 3L);
        assertFalse(h.isActiva());
        verify(repo).guardar(h);
    }

    // ---------- contrato / consultas ----------

    @Test
    void existeHotelActivoSoloParaHotelesActivosConIdValido() {
        assertTrue(servicio.existeHotelActivo(1L));
        assertFalse(servicio.existeHotelActivo(null));
        assertFalse(servicio.existeHotelActivo(0L));
        assertFalse(servicio.existeHotelActivo(33L));
        hotel.darDeBaja();
        assertFalse(servicio.existeHotelActivo(1L));
    }

    @Test
    void existeTipoHabitacionActivoRecortaElCodigoYValidaEstados() {
        when(repo.buscarTipoPorCodigo(1L, "DOBLE")).thenReturn(Optional.of(tipo));
        assertTrue(servicio.existeTipoHabitacionActivo(1L, "  DOBLE "));
        assertFalse(servicio.existeTipoHabitacionActivo(null, "DOBLE"));
        assertFalse(servicio.existeTipoHabitacionActivo(0L, "DOBLE"));
        assertFalse(servicio.existeTipoHabitacionActivo(1L, null));
        assertFalse(servicio.existeTipoHabitacionActivo(1L, "  "));
        assertFalse(servicio.existeTipoHabitacionActivo(1L, "OTRO"));
        tipo.darDeBaja();
        assertFalse(servicio.existeTipoHabitacionActivo(1L, "DOBLE"));
    }

    @Test
    void existeTipoHabitacionActivoFalseSiElHotelEstaDeBaja() {
        when(repo.buscarTipoPorCodigo(1L, "DOBLE")).thenReturn(Optional.of(tipo));
        hotel.darDeBaja();
        assertFalse(servicio.existeTipoHabitacionActivo(1L, "DOBLE"));
    }

    @Test
    void existeHabitacionActivaValidaIdsEstadoYPertenencia() {
        Habitacion h = habitacion(3L, "101");
        assertTrue(servicio.existeHabitacionActiva(1L, 3L));
        assertFalse(servicio.existeHabitacionActiva(null, 3L));
        assertFalse(servicio.existeHabitacionActiva(1L, null));
        assertFalse(servicio.existeHabitacionActiva(0L, 3L));
        assertFalse(servicio.existeHabitacionActiva(1L, -1L));
        assertFalse(servicio.existeHabitacionActiva(2L, 3L));
        assertFalse(servicio.existeHabitacionActiva(1L, 99L));
        h.darDeBaja();
        assertFalse(servicio.existeHabitacionActiva(1L, 3L));
    }

    @Test
    void existeHabitacionActivaFalseSiElHotelEstaDeBaja() {
        habitacion(3L, "101");
        hotel.darDeBaja();
        assertFalse(servicio.existeHabitacionActiva(1L, 3L));
    }

    @Test
    void consultarCapacidadTipoDevuelveLaCapacidad() {
        when(repo.buscarTipoPorCodigo(1L, "DOBLE")).thenReturn(Optional.of(tipo));
        assertEquals(2, servicio.consultarCapacidadTipo(1L, " DOBLE "));
    }

    @Test
    void consultarCapacidadTipoRechazaEntradasInvalidas() {
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.consultarCapacidadTipo(null, "X"));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.consultarCapacidadTipo(1L, null));
        assertHotel(CodigoErrorHotel.SOLICITUD_INVALIDA, () -> servicio.consultarCapacidadTipo(1L, " "));
    }

    @Test
    void consultarCapacidadTipoInexistenteOConHotelDeBajaNoSeEncuentra() {
        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO, () -> servicio.consultarCapacidadTipo(1L, "ZZZ"));
        when(repo.buscarTipoPorCodigo(1L, "DOBLE")).thenReturn(Optional.of(tipo));
        hotel.darDeBaja();
        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO, () -> servicio.consultarCapacidadTipo(1L, "DOBLE"));
    }

    @Test
    void consultarCapacidadTipoInactivoNoSeEncuentra() {
        when(repo.buscarTipoPorCodigo(1L, "DOBLE")).thenReturn(Optional.of(tipo));
        tipo.darDeBaja();
        assertHotel(CodigoErrorHotel.TIPO_HABITACION_NO_ENCONTRADO, () -> servicio.consultarCapacidadTipo(1L, "DOBLE"));
    }

    // ---------- esViolacionUnicidad ----------

    @Test
    void esViolacionUnicidadRecorreLaCadenaDeCausas() {
        Throwable profundo = new RuntimeException(new IllegalStateException(new SQLException("x", "23505")));
        assertTrue(ServicioDeHotelesImpl.esViolacionUnicidad(profundo));
        assertTrue(ServicioDeHotelesImpl.esViolacionUnicidad(new SQLException("x", "23505")));
    }

    @Test
    void esViolacionUnicidadIgnoraOtrosEstadosYCausasNoSql() {
        assertFalse(ServicioDeHotelesImpl.esViolacionUnicidad(new SQLException("x", "08006")));
        assertFalse(ServicioDeHotelesImpl.esViolacionUnicidad(new SQLException("x")));
        assertFalse(ServicioDeHotelesImpl.esViolacionUnicidad(new RuntimeException("23505")));
        assertFalse(ServicioDeHotelesImpl.esViolacionUnicidad(null));
    }

    @Test
    void crearHabitacionNoTraduceErroresDeOtraNaturaleza() {
        when(repo.guardar(any(Habitacion.class))).thenAnswer(i -> i.getArgument(0));
        IllegalStateException original = new IllegalStateException("caida");
        doThrow(original).when(repo).sincronizar();

        var recibido = assertThrows(IllegalStateException.class,
                () -> servicio.crearHabitacion(1L, new HabitacionRequest(2L, "9", 1, null)));
        assertSame(original, recibido);
        verify(repo).buscarHabitacionPorNumero(eq(1L), anyString());
    }
}
