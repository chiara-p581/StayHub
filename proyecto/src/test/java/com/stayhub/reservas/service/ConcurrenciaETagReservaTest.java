package com.stayhub.reservas.service;

import static com.stayhub.seguridad.IdentidadFixture.campo;
import static com.stayhub.seguridad.IdentidadFixture.usuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.pagos.repository.PagoRepository;
import com.stayhub.reservas.contrato.GestionDeDisponibilidadPort;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.exception.CodigoErrorReserva;
import com.stayhub.reservas.exception.ReservaException;
import com.stayhub.reservas.model.Huesped;
import com.stayhub.reservas.model.Reserva;
import com.stayhub.reservas.repository.ReservaRepository;
import com.stayhub.seguridad.SeguridadException;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import jakarta.enterprise.inject.Instance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KAN-96 - Control de concurrencia con ETag / If-Match sobre PUT /reservas/{id}.
 *
 * Criterios de aceptación:
 *  1. Una actualización con ETag vigente se aplica.
 *  2. Una actualización con ETag obsoleto devuelve 412.
 *  3. El dato más reciente no se sobrescribe.
 *
 * El mapeo a HTTP (VERSION_OBSOLETA -> 412, VERSION_REQUERIDA -> 428)
 * se prueba en com.stayhub.reservas.api.ETagReservaTest.
 */
class ConcurrenciaETagReservaTest {

    ServicioDeReservasImpl servicio;
    ReservaRepository repo;
    GestionDeDisponibilidadPort cupo;
    LocalDate desde = LocalDate.now().plusDays(10), hasta = desde.plusDays(3);
    Reserva reserva;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() throws Exception {
        servicio = new ServicioDeReservasImpl();
        repo = mock(ReservaRepository.class);
        cupo = mock(GestionDeDisponibilidadPort.class);
        var tarifas = mock(ServicioDeInventarioYTarifas.class);
        Instance<GestionDeDisponibilidadPort> instancia = mock(Instance.class);
        when(instancia.isResolvable()).thenReturn(true);
        when(instancia.get()).thenReturn(cupo);
        campo(servicio, "repositorio", repo);
        campo(servicio, "pagos", mock(PagoRepository.class));
        campo(servicio, "usuarios", mock(ServicioDeUsuarios.class));
        campo(servicio, "tarifas", tarifas);
        campo(servicio, "disponibilidad", instancia);
        campo(servicio, "identidad", usuario(1L, "HUESPED"));
        when(tarifas.consultarTarifas(1L, desde, hasta)).thenReturn(List.of(
                new TarifaDTO(1L, "DOBLE", desde, hasta, new BigDecimal("100000"), "ARS")));
        when(cupo.reemplazarHold(any(), any(), any(), anyInt(), any(), any())).thenReturn("hold-2", "hold-3");

        reserva = new Reserva("DIRECTA", null, 1L, "DOBLE", 1, desde, hasta,
                new Huesped("Ana", "Paz", "ana@mail.com", "123"), new BigDecimal("100000"), "ARS");
        reserva.asignarPropietario(1L);
        reserva.iniciarHold("hold-1");
        versionEnBase(0L);
        when(repo.buscarPorIdBloqueando(7L)).thenReturn(Optional.of(reserva));
    }

    /** Simula lo que hace JPA al confirmar una transacción: incrementa @Version. */
    void versionEnBase(long version) throws Exception {
        var campo = Reserva.class.getDeclaredField("version");
        campo.setAccessible(true);
        campo.set(reserva, version);
    }

    ReservaRequest cambio(int habitaciones) {
        // El precio enviado es obligatorio pero no manda: el servidor lo recalcula con la tarifa.
        return new ReservaRequest(1L, "DOBLE", habitaciones, desde, hasta, null, null, null, null, BigDecimal.ONE, "ARS");
    }

    @Test
    @DisplayName("Criterio 1: una actualización con ETag vigente se aplica")
    void etagVigenteSeAplica() {
        var respuesta = servicio.modificarReserva(7L, cambio(2), 0L);

        assertEquals(2, respuesta.cantidadHabitaciones());
        assertEquals(new BigDecimal("200000"), respuesta.precioTotal());
        assertEquals(2, reserva.getCantidadHabitaciones());
        verify(repo).guardar(reserva);
        verify(repo).sincronizar(); // flush: la respuesta lleva la versión nueva para el próximo If-Match
    }

    @Test
    @DisplayName("Criterio 2: una actualización con ETag obsoleto devuelve 412")
    void etagObsoletoDevuelve412() throws Exception {
        versionEnBase(3L);

        var error = assertThrows(ReservaException.class, () -> servicio.modificarReserva(7L, cambio(2), 2L));

        assertEquals(CodigoErrorReserva.VERSION_OBSOLETA, error.getCodigo());
        verifyNoInteractions(cupo);
        verify(repo, never()).guardar(any());
    }

    @Test
    @DisplayName("Criterio 3: el dato más reciente no se sobrescribe (dos clientes leen la misma versión)")
    void elDatoMasRecienteNoSeSobrescribe() throws Exception {
        // Ana y Bruno consultan la reserva al mismo tiempo: ambos reciben ETag "0".
        long etagLeidoPorAna = 0L, etagLeidoPorBruno = 0L;

        // Ana guarda primero: se aplica y la base pasa a versión 1.
        servicio.modificarReserva(7L, cambio(3), etagLeidoPorAna);
        versionEnBase(1L);

        // Bruno intenta guardar su cambio con el ETag viejo.
        var error = assertThrows(ReservaException.class,
                () -> servicio.modificarReserva(7L, cambio(1), etagLeidoPorBruno));

        assertEquals(CodigoErrorReserva.VERSION_OBSOLETA, error.getCodigo()); // -> HTTP 412
        assertEquals(3, reserva.getCantidadHabitaciones(), "se conserva el cambio de Ana");
        assertEquals(new BigDecimal("300000"), reserva.getPrecioTotal());
        verify(cupo, times(1)).reemplazarHold(any(), any(), any(), anyInt(), any(), any());
        verify(repo, times(1)).guardar(any());
    }

    @Test
    @DisplayName("Sin If-Match la modificación se rechaza con 428 y no toca nada")
    void sinIfMatchDevuelve428() {
        var error = assertThrows(ReservaException.class, () -> servicio.modificarReserva(7L, cambio(2), null));

        assertEquals(CodigoErrorReserva.VERSION_REQUERIDA, error.getCodigo());
        assertEquals(1, reserva.getCantidadHabitaciones());
        verifyNoInteractions(cupo);
    }

    @Test
    @DisplayName("If-Match: * omite el chequeo de versión")
    void ifMatchAsteriscoAceptaCualquierVersion() throws Exception {
        versionEnBase(8L);
        var respuesta = servicio.modificarReserva(7L, cambio(2), ServicioDeReservas.CUALQUIER_VERSION);
        assertEquals(2, respuesta.cantidadHabitaciones());
    }

    @Test
    @DisplayName("La autorización se evalúa antes que la versión (no se filtra info de reservas ajenas)")
    void reservaAjenaSigueDando403AntesQueLaVersion() throws Exception {
        var ajena = new Reserva("DIRECTA", null, 1L, "DOBLE", 1, desde, hasta,
                new Huesped("Otro", "Huesped", "otro@mail.com", "123"), new BigDecimal("100000"), "ARS");
        ajena.asignarPropietario(2L);
        when(repo.buscarPorIdBloqueando(8L)).thenReturn(Optional.of(ajena));
        assertThrows(SeguridadException.class, () -> servicio.modificarReserva(8L, cambio(2), null));
        verifyNoInteractions(cupo);
    }

    @Test
    @DisplayName("La respuesta expone la versión actual para armar el ETag")
    void respuestaIncluyeVersion() throws Exception {
        versionEnBase(5L);
        when(repo.buscarPorId(7L)).thenReturn(Optional.of(reserva));
        assertEquals(5L, servicio.consultarReserva(7L).version());
    }
}
