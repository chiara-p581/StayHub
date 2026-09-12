package com.stayhub.reservas.service;

import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.model.EstadoReserva;
import com.stayhub.reservas.model.Huesped;
import com.stayhub.reservas.model.Reserva;
import com.stayhub.reservas.repository.ReservaRepository;

import jakarta.enterprise.inject.Instance;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Cubre verificarPropietario, la verificación de dueño en cancelarReserva y
 * modificarReserva: un HUESPED solo puede operar sobre SU reserva (comparando
 * actorEmail contra el huespedEmail cargado en la reserva), un ADMIN puede
 * operar sobre cualquiera.
 *
 * Esta protección se había perdido cuando se reemplazó el login manejado por
 * el contenedor de EJBs (SessionContext) por el login por sesión propio
 * (POST /usuarios/login + AutenticacionFilter): ReservaResource ahora
 * resuelve actorEmail/actorEsAdmin desde la sesión HTTP y se los pasa al
 * servicio como parámetros explícitos, en lugar de que el servicio pregunte
 * al contenedor quién llama.
 */
class ServicioDeReservasImplTest {

    private ServicioDeReservasImpl servicio;
    private ReservaRepository repositorio;
    private GestionDeDisponibilidadPort disponibilidadPort;

    @BeforeEach
    void preparar() throws Exception {
        servicio = new ServicioDeReservasImpl();
        repositorio = mock(ReservaRepository.class);
        setPrivateField(servicio, "repositorio", repositorio);

        // verificarPropietario corta antes de tocar disponibilidad en los casos de
        // "ajena"/"sin sesión", pero el caso de modificar sí llega a usarla, así que
        // queda resolvible y con un hold de prueba para no depender de un componente real.
        disponibilidadPort = mock(GestionDeDisponibilidadPort.class);
        when(disponibilidadPort.crearHold(any(), any(), anyInt(), any(), any())).thenReturn("hold-nuevo");
        @SuppressWarnings("unchecked")
        Instance<GestionDeDisponibilidadPort> disponibilidad = mock(Instance.class);
        when(disponibilidad.isResolvable()).thenReturn(true);
        when(disponibilidad.get()).thenReturn(disponibilidadPort);
        setPrivateField(servicio, "disponibilidad", disponibilidad);
    }

    private static void setPrivateField(Object objetivo, String nombre, Object valor) throws Exception {
        Field campo = ServicioDeReservasImpl.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(objetivo, valor);
    }

    private Reserva reservaConfirmadaDe(String emailDueño) {
        Reserva reserva = new Reserva("DIRECTA", null, 1L, "DOBLE", 1,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                new Huesped("Ana", "Paz", emailDueño, "1111"),
                BigDecimal.valueOf(50000), "ARS");
        reserva.confirmar("hold-1");
        return reserva;
    }

    @Test
    void huespedPuedeCancelarSuPropiaReserva() {
        Reserva propia = reservaConfirmadaDe("huesped1@test.com");
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(propia));

        var respuesta = servicio.cancelarReserva(1L, "huesped1@test.com", false);

        assertEquals("CANCELADA", respuesta.estado());
        verify(repositorio).guardar(propia);
    }

    @Test
    void huespedNoPuedeCancelarUnaReservaAjena() {
        Reserva ajena = reservaConfirmadaDe("otrapersona@test.com");
        when(repositorio.buscarPorId(2L)).thenReturn(Optional.of(ajena));

        ReservaException error = assertThrows(ReservaException.class,
                () -> servicio.cancelarReserva(2L, "huesped1@test.com", false));

        assertEquals(CodigoErrorReserva.NO_AUTORIZADO, error.getCodigo());
        assertEquals(EstadoReserva.CONFIRMADA, ajena.getEstado());
        verify(repositorio, never()).guardar(any());
    }

    @Test
    void adminPuedeCancelarCualquierReserva() {
        Reserva ajena = reservaConfirmadaDe("otrapersona@test.com");
        when(repositorio.buscarPorId(3L)).thenReturn(Optional.of(ajena));

        var respuesta = servicio.cancelarReserva(3L, "admin@test.com", true);

        assertEquals("CANCELADA", respuesta.estado());
    }

    @Test
    void sinSesionNoPuedeOperarSobreNingunaReserva() {
        Reserva propia = reservaConfirmadaDe("huesped1@test.com");
        when(repositorio.buscarPorId(4L)).thenReturn(Optional.of(propia));

        ReservaException error = assertThrows(ReservaException.class,
                () -> servicio.cancelarReserva(4L, null, false));

        assertEquals(CodigoErrorReserva.NO_AUTORIZADO, error.getCodigo());
    }

    @Test
    void huespedNoPuedeModificarUnaReservaAjena() {
        Reserva ajena = reservaConfirmadaDe("otrapersona@test.com");
        when(repositorio.buscarPorId(5L)).thenReturn(Optional.of(ajena));
        ReservaRequest cambios = new ReservaRequest(1L, "DOBLE", 1,
                LocalDate.now().plusDays(2), LocalDate.now().plusDays(4),
                null, null, null, null, BigDecimal.valueOf(60000), "ARS");

        ReservaException error = assertThrows(ReservaException.class,
                () -> servicio.modificarReserva(5L, cambios, "huesped1@test.com", false));

        assertEquals(CodigoErrorReserva.NO_AUTORIZADO, error.getCodigo());
        verify(repositorio, never()).guardar(any());
    }

    @Test
    void huespedPuedeModificarSuPropiaReservaConfirmada() {
        Reserva propia = reservaConfirmadaDe("huesped1@test.com"); // CONFIRMADA, holdId="hold-1"
        when(repositorio.buscarPorId(6L)).thenReturn(Optional.of(propia));
        when(disponibilidadPort.reemplazarHold(eq("hold-1"), any(), any(), anyInt(), any(), any()))
                .thenReturn("hold-2");
        ReservaRequest cambios = new ReservaRequest(1L, "DOBLE", 1,
                LocalDate.now().plusDays(2), LocalDate.now().plusDays(4),
                null, null, null, null, BigDecimal.valueOf(60000), "ARS");

        var respuesta = servicio.modificarReserva(6L, cambios, "huesped1@test.com", false);

        // MODIFICADA es transitorio: al estar CONFIRMADA antes del cambio, se
        // reconfirma con el hold nuevo y queda CONFIRMADA (ver README del componente).
        assertEquals("CONFIRMADA", respuesta.estado());
        verify(disponibilidadPort).confirmarHold("hold-2");
        verify(repositorio).guardar(propia);
    }
}
