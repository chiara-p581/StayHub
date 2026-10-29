package com.stayhub.servicioDeOverbooking.service;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.contrato.SinDisponibilidadException;
import com.stayhub.seguridad.SeguridadException;
import com.stayhub.servicioDeOverbooking.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.servicioDeOverbooking.dto.ConflictoReservaDTO;
import com.stayhub.servicioDeOverbooking.dto.DisponibilidadDTO;
import com.stayhub.servicioDeOverbooking.dto.ResultadoResolucionOverbookingDTO;
import com.stayhub.servicioDeOverbooking.exception.CodigoErrorOverbooking;
import com.stayhub.servicioDeOverbooking.exception.OverbookingException;
import com.stayhub.servicioDeOverbooking.messaging.PublicadorSolicitudNotificacionAsincrona;
import com.stayhub.servicioDeOverbooking.messaging.SolicitudNotificacionAsincrona;
import com.stayhub.servicioDeOverbooking.model.ConflictoOverbooking;
import com.stayhub.servicioDeOverbooking.model.EstrategiaResolucion;
import com.stayhub.servicioDeOverbooking.repository.ConflictoOverbookingRepository;
import jakarta.enterprise.inject.Instance;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Resolución de overbooking: validación, elección de alternativa, compensación y notificación. */
class ServicioDeOverbookingImplTest {

    private final LocalDate desde = LocalDate.now().plusDays(5);
    private final LocalDate hasta = desde.plusDays(3);

    private ServicioDeOverbookingImpl servicio;
    private ServicioDeInventarioYTarifasPort lectura;
    private GestionDeDisponibilidadPort cupo;
    private ConflictoOverbookingRepository repositorio;
    private PublicadorSolicitudNotificacionAsincrona publicador;
    private Instance<ServicioDeInventarioYTarifasPort> instanciaLectura;
    private Instance<GestionDeDisponibilidadPort> instanciaCupo;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() throws Exception {
        servicio = new ServicioDeOverbookingImpl();
        lectura = mock(ServicioDeInventarioYTarifasPort.class);
        cupo = mock(GestionDeDisponibilidadPort.class);
        repositorio = mock(ConflictoOverbookingRepository.class);
        publicador = mock(PublicadorSolicitudNotificacionAsincrona.class);
        instanciaLectura = mock(Instance.class);
        instanciaCupo = mock(Instance.class);
        when(instanciaLectura.isResolvable()).thenReturn(true);
        when(instanciaLectura.get()).thenReturn(lectura);
        when(instanciaCupo.isResolvable()).thenReturn(true);
        when(instanciaCupo.get()).thenReturn(cupo);
        inyectar("repositorio", repositorio);
        inyectar("publicadorNotificacion", publicador);
        inyectar("inventarioLectura", instanciaLectura);
        inyectar("disponibilidad", instanciaCupo);
    }

    private void inyectar(String campo, Object valor) throws Exception {
        var f = ServicioDeOverbookingImpl.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(servicio, valor);
    }

    private ConflictoReservaDTO dto(String email) {
        return new ConflictoReservaDTO(13L, 33L, "STANDARD", 2, desde, hasta, "BOOKING", "REF-1", email);
    }

    private DisponibilidadDTO disp(String tipo, int unidades) {
        return new DisponibilidadDTO(33L, tipo, desde, hasta, unidades);
    }

    @Test
    void reubicaEnLaPrimeraAlternativaConCupoSuficienteYGuardaElConflicto() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of(
                disp("STANDARD", 9),   // mismo tipo: se descarta
                disp("DELUXE", 1),     // no alcanza para 2 habitaciones
                disp("SUITE", 2),      // primera válida (borde: unidades == cantidad)
                disp("JUNIOR", 5)));
        when(cupo.crearHold(33L, "SUITE", 2, desde, hasta)).thenReturn("hold-1");

        ResultadoResolucionOverbookingDTO r = servicio.resolverConflicto(dto(null));

        assertTrue(r.resuelto());
        assertEquals(EstrategiaResolucion.REUBICACION, r.estrategia());
        assertEquals("SUITE", r.tipoHabitacionAlternativa());
        assertEquals("hold-1", r.holdAlternativoId());
        assertEquals("Reubicado a tipoHabitacion=SUITE", r.mensaje());
        assertEquals(13L, r.reservaIdConflictiva());
        assertNotNull(r.fecha());
        verify(cupo).confirmarHold("hold-1");
        verify(cupo, never()).crearHold(anyLong(), org.mockito.ArgumentMatchers.eq("JUNIOR"), anyInt(), any(), any());
        ArgumentCaptor<ConflictoOverbooking> guardado = ArgumentCaptor.forClass(ConflictoOverbooking.class);
        verify(repositorio).guardar(guardado.capture());
        assertEquals(33L, guardado.getValue().getHotelId());
        assertEquals("STANDARD", guardado.getValue().getTipoHabitacion());
        assertEquals(2, guardado.getValue().getCantidadHabitaciones());
        assertEquals("BOOKING", guardado.getValue().getCanalOrigen());
        assertEquals("REF-1", guardado.getValue().getReferenciaExterna());
        assertEquals(EstrategiaResolucion.REUBICACION, guardado.getValue().getEstrategiaAplicada());
    }

    private static long anyLong() { return org.mockito.ArgumentMatchers.anyLong(); }

    @Test
    void sinCandidatosResuelvePorCompensacionYNoTocaElCupo() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of());

        ResultadoResolucionOverbookingDTO r = servicio.resolverConflicto(dto(null));

        assertEquals(EstrategiaResolucion.COMPENSACION, r.estrategia());
        assertTrue(r.resuelto());
        assertNull(r.holdAlternativoId());
        assertNull(r.tipoHabitacionAlternativa());
        assertTrue(r.mensaje().startsWith("Sin disponibilidad alternativa"));
        verifyNoInteractions(cupo);
        verify(repositorio).guardar(any(ConflictoOverbooking.class));
    }

    @Test
    void soloHayDelMismoTipoOCupoInsuficienteCompensa() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta))
                .thenReturn(List.of(disp("STANDARD", 10), disp("SUITE", 1)));

        ResultadoResolucionOverbookingDTO r = servicio.resolverConflicto(dto(null));

        assertEquals(EstrategiaResolucion.COMPENSACION, r.estrategia());
        verifyNoInteractions(cupo);
    }

    @Test
    void alternativaQueSeAgotaAlRetenerCompensaSinConfirmarNada() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of(disp("SUITE", 3)));
        when(cupo.crearHold(33L, "SUITE", 2, desde, hasta)).thenThrow(new SinDisponibilidadException("sin cupo"));

        ResultadoResolucionOverbookingDTO r = servicio.resolverConflicto(dto(null));

        assertEquals(EstrategiaResolucion.COMPENSACION, r.estrategia());
        assertTrue(r.mensaje().contains("dejó de estar disponible"));
        assertNull(r.holdAlternativoId());
        verify(cupo, never()).confirmarHold(anyString());
    }

    @Test
    void siFallaLaConfirmacionDelHoldTambienCompensa() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of(disp("SUITE", 3)));
        when(cupo.crearHold(33L, "SUITE", 2, desde, hasta)).thenReturn("hold-9");
        doThrow(new SinDisponibilidadException("ya no")).when(cupo).confirmarHold("hold-9");

        ResultadoResolucionOverbookingDTO r = servicio.resolverConflicto(dto(null));

        assertEquals(EstrategiaResolucion.COMPENSACION, r.estrategia());
        assertNull(r.holdAlternativoId());
    }

    @Test
    void conEmailPublicaUnaNotificacionConElMensajeDeResolucion() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of());

        servicio.resolverConflicto(dto("ana@test.com"));

        ArgumentCaptor<SolicitudNotificacionAsincrona> captor = ArgumentCaptor.forClass(SolicitudNotificacionAsincrona.class);
        verify(publicador).publicar(captor.capture());
        SolicitudNotificacionAsincrona s = captor.getValue();
        assertEquals("ana@test.com", s.destinatario());
        assertEquals(TipoEvento.RESULTADO_OVERBOOKING, s.tipoEvento());
        assertEquals(CanalNotificacion.EMAIL, s.canalPreferido());
        assertEquals("Cambio en tu reserva", s.asunto());
        assertTrue(s.mensaje().startsWith("Sin disponibilidad alternativa"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void sinEmailUtilNoSeNotifica(String email) {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of());

        servicio.resolverConflicto(dto(email));

        verifyNoInteractions(publicador);
        verify(repositorio).guardar(any(ConflictoOverbooking.class));
    }

    @Test
    void sinServicioDeInventarioFallaConDependenciaYNoGuarda() {
        when(instanciaLectura.isResolvable()).thenReturn(false);

        OverbookingException ex = assertThrows(OverbookingException.class, () -> servicio.resolverConflicto(dto(null)));

        assertEquals(CodigoErrorOverbooking.DEPENDENCIA_NO_DISPONIBLE, ex.getCodigo());
        verifyNoInteractions(repositorio, publicador);
    }

    @Test
    void sinGestionDeDisponibilidadFallaConDependenciaYNoGuarda() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of(disp("SUITE", 3)));
        when(instanciaCupo.isResolvable()).thenReturn(false);

        OverbookingException ex = assertThrows(OverbookingException.class, () -> servicio.resolverConflicto(dto("a@b.com")));

        assertEquals(CodigoErrorOverbooking.DEPENDENCIA_NO_DISPONIBLE, ex.getCodigo());
        verify(repositorio, never()).guardar(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void conflictoNuloDevuelve400() {
        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.resolverConflicto(null));
        assertEquals(400, ex.getEstado());
        verifyNoInteractions(lectura, repositorio);
    }

    @Test
    void fechasInvalidasDevuelven400SinConsultarInventario() {
        var sinFechas = new ConflictoReservaDTO(13L, 33L, "STANDARD", 1, null, null, null, null, null);
        var invertidas = new ConflictoReservaDTO(13L, 33L, "STANDARD", 1, hasta, desde, null, null, null);
        var mismoDia = new ConflictoReservaDTO(13L, 33L, "STANDARD", 1, desde, desde, null, null, null);
        var demasiadoLargo = new ConflictoReservaDTO(13L, 33L, "STANDARD", 1, desde, desde.plusDays(32), null, null, null);
        for (var d : List.of(sinFechas, invertidas, mismoDia, demasiadoLargo)) {
            assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.resolverConflicto(d)).getEstado());
        }
        verifyNoInteractions(lectura, repositorio);
    }

    @Test
    void cantidadDeHabitacionesFueraDeRangoDevuelve400() {
        for (int cantidad : new int[]{0, -1, 11}) {
            var d = new ConflictoReservaDTO(13L, 33L, "STANDARD", cantidad, desde, hasta, null, null, null);
            assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.resolverConflicto(d)).getEstado());
        }
        verifyNoInteractions(lectura, repositorio);
    }

    @Test
    void faltanteDeReservaHotelOTipoEsSolicitudInvalida() {
        var sinReserva = new ConflictoReservaDTO(null, 33L, "STANDARD", 1, desde, hasta, null, null, null);
        var sinHotel = new ConflictoReservaDTO(13L, null, "STANDARD", 1, desde, hasta, null, null, null);
        var sinTipo = new ConflictoReservaDTO(13L, 33L, null, 1, desde, hasta, null, null, null);
        var tipoEnBlanco = new ConflictoReservaDTO(13L, 33L, "  ", 1, desde, hasta, null, null, null);
        for (var d : List.of(sinReserva, sinHotel, sinTipo, tipoEnBlanco)) {
            OverbookingException ex = assertThrows(OverbookingException.class, () -> servicio.resolverConflicto(d));
            assertEquals(CodigoErrorOverbooking.SOLICITUD_INVALIDA, ex.getCodigo());
        }
        verifyNoInteractions(lectura, repositorio, publicador);
    }

    @Test
    void elLimiteDeUnaHabitacionYDiezHabitacionesEsValido() {
        when(lectura.consultarDisponibilidad(33L, desde, hasta)).thenReturn(List.of());
        for (int cantidad : new int[]{1, 10}) {
            var d = new ConflictoReservaDTO(13L, 33L, "STANDARD", cantidad, desde, hasta, null, null, null);
            assertTrue(servicio.resolverConflicto(d).resuelto());
        }
    }
}
