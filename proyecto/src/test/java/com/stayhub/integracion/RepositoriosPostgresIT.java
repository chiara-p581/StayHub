package com.stayhub.integracion;

import com.stayhub.canalesexternos.messaging.SincronizacionIdempotente;
import com.stayhub.hoteles.model.Hotel;
import com.stayhub.hoteles.repository.HotelRepositoryJpa;
import com.stayhub.inventarioytarifas.model.EstadoHold;
import com.stayhub.inventarioytarifas.model.Hold;
import com.stayhub.inventarioytarifas.model.InventarioDiario;
import com.stayhub.inventarioytarifas.repository.HoldRepositoryJpa;
import com.stayhub.inventarioytarifas.repository.InventarioDiarioRepositoryJpa;
import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.servicioDeOverbooking.model.ConflictoOverbooking;
import com.stayhub.servicioDeOverbooking.model.EstrategiaResolucion;
import com.stayhub.servicioDeOverbooking.repository.ConflictoOverbookingRepositoryJpa;
import com.stayhub.usuarios.model.RolUsuario;
import com.stayhub.usuarios.model.Usuario;
import com.stayhub.usuarios.repository.UsuarioRepositoryJpa;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.stayhub.integracion.EntornoIntegracion.contar;
import static com.stayhub.integracion.EntornoIntegracion.emf;
import static com.stayhub.integracion.EntornoIntegracion.enTransaccion;
import static com.stayhub.integracion.EntornoIntegracion.inyectar;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Los repositorios JPA de StayHub y las claves de idempotencia contra un PostgreSQL REAL
 * (contenedor), con el esquema de docker/postgres/01-schema.sql.
 */
class RepositoriosPostgresIT {

    private static long idUnico() {
        return 9_000_000L + Math.floorMod(System.nanoTime(), 900_000L);
    }

    @Test
    void elEsquemaRealCoincideConTodasLasEntidades() {
        // Crear la EntityManagerFactory ya valida (hbm2ddl=validate) cada tabla y columna
        assertTrue(emf().getMetamodel().getEntities().size() >= 11,
                "Deberían estar mapeadas todas las entidades de StayHub");
    }

    @Test
    void conflictoOverbookingSeGuardaYSeLeeConSusDatos() {
        var repositorio = new ConflictoOverbookingRepositoryJpa();
        String referencia = "EXT-" + UUID.randomUUID();

        Long id = enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            var conflicto = new ConflictoOverbooking(55L, 1L, "DOBLE", 2,
                    LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 4),
                    "BOOKING", referencia, "ana@example.com");
            conflicto.resolverPorReubicacion("SUITE", "hold-it-1", "Reubicado a SUITE");
            repositorio.guardar(conflicto);
            return conflicto.getId();
        });
        assertNotNull(id, "PostgreSQL debe asignar el id (IDENTITY)");

        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            var leido = repositorio.buscarPorId(id).orElseThrow();
            assertEquals(referencia, leido.getReferenciaExterna());
            assertEquals(EstrategiaResolucion.REUBICACION, leido.getEstrategiaAplicada());
            assertEquals("SUITE", leido.getTipoHabitacionAlternativa());
            assertEquals("hold-it-1", leido.getHoldAlternativoId());
            assertEquals(LocalDate.of(2027, 3, 4), leido.getCheckOut());
            assertTrue(leido.isResuelto());
            return null;
        });
    }

    @Test
    void holdSeGuardaYPasaDePendienteAConfirmado() {
        var repositorio = new HoldRepositoryJpa();
        String holdId = UUID.randomUUID().toString();

        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            return repositorio.guardar(new Hold(holdId, 1L, "DOBLE", 1,
                    LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 3)));
        });
        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            assertEquals(EstadoHold.PENDIENTE, repositorio.buscarPorId(holdId).orElseThrow().getEstado());
            Hold hold = repositorio.buscarPorId(holdId).orElseThrow();
            hold.confirmar();
            repositorio.guardar(hold);
            return null;
        });
        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            assertEquals(EstadoHold.CONFIRMADO, repositorio.buscarPorId(holdId).orElseThrow().getEstado());
            return null;
        });
    }

    @Test
    void inventarioCargaUnRangoConSqlNativoDePostgresYLoActualiza() {
        var repositorio = new InventarioDiarioRepositoryJpa();
        long hotelId = idUnico();
        LocalDate desde = LocalDate.of(2027, 5, 1);
        LocalDate hasta = LocalDate.of(2027, 5, 4); // [desde, hasta): 3 noches

        // generate_series + ON CONFLICT son específicos de PostgreSQL: con otra base no correrían
        int filas = enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            return repositorio.cargarRango(hotelId, "DOBLE", desde, hasta, 5, new BigDecimal("100.00"), "ARS");
        });
        assertEquals(3, filas);

        // Repetir la carga actualiza (upsert), no duplica filas
        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            return repositorio.cargarRango(hotelId, "DOBLE", desde, hasta, 8, new BigDecimal("120.00"), "ARS");
        });

        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            List<InventarioDiario> dias = repositorio.buscarPorHotelYRango(hotelId, desde, hasta);
            assertEquals(3, dias.size());
            assertTrue(dias.stream().allMatch(d -> d.getUnidadesTotales() == 8));
            assertTrue(dias.stream().allMatch(d -> d.getPrecio().compareTo(new BigDecimal("120.00")) == 0));

            // SELECT ... FOR UPDATE (bloqueo pesimista) también lo acepta el PostgreSQL real
            var bloqueado = repositorio.buscarPorHotelTipoYFechaBloqueando(hotelId, "DOBLE", desde);
            assertTrue(bloqueado.isPresent());
            assertEquals(8, bloqueado.get().getUnidadesDisponibles());
            return null;
        });
    }

    @Test
    void hotelGuardaSusServiciosEnLaTablaAuxiliar() {
        var repositorio = new HotelRepositoryJpa();
        String nombre = "Hotel IT " + UUID.randomUUID();

        Long id = enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            Hotel hotel = repositorio.guardar(new Hotel(nombre, "Calle 123", "Mar del Plata", "Argentina",
                    "Hotel de prueba de integración", Set.of("WIFI", "PILETA")));
            repositorio.sincronizar();
            return hotel.getId();
        });

        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            Hotel leido = repositorio.buscarHotel(id).orElseThrow();
            assertEquals(nombre, leido.getNombre());
            assertEquals(Set.of("WIFI", "PILETA"), leido.getServicios());
            assertTrue(repositorio.listarHoteles(false).stream().anyMatch(h -> h.getId().equals(id)));
            return null;
        });
    }

    @Test
    void usuarioRechazaUnEmailDuplicadoPorLaRestriccionUnicaDeLaBase() {
        var repositorio = new UsuarioRepositoryJpa();
        String email = "it-" + UUID.randomUUID() + "@example.com";

        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            return repositorio.guardar(new Usuario(email, "hash-1", "Ana", "Pérez", RolUsuario.HUESPED));
        });
        enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            assertTrue(repositorio.buscarPorEmail(email).isPresent());
            return null;
        });

        // El UNIQUE de usuario.email lo hace cumplir PostgreSQL, no solo el código
        assertThrows(PersistenceException.class, () -> enTransaccion(em -> {
            inyectar(repositorio, "em", em);
            repositorio.guardar(new Usuario(email, "hash-2", "Otra", "Persona", RolUsuario.HUESPED));
            em.flush();
            return null;
        }));
    }

    // ------------------------------------------------ claves de idempotencia con concurrencia real

    @Test
    void dosConsumidoresConLaMismaClaveEnvianUnSoloCorreoGraciasAlBloqueoDePostgres() throws Exception {
        String clave = "it-correo:" + UUID.randomUUID();
        EmailNotificador email = mock(EmailNotificador.class);
        // El envío tarda un poco: si no hubiera bloqueo, el otro hilo entraría mientras tanto
        doAnswer(invocacion -> {
            Thread.sleep(400);
            return null;
        }).when(email).enviar(any(SolicitudNotificacionDTO.class));

        var solicitud = new SolicitudNotificacionDTO("ana@example.com", TipoEvento.BIENVENIDA,
                CanalNotificacion.EMAIL, "Asunto", "Cuerpo");

        ejecutarEnParalelo(() -> enTransaccion(em -> {
            var servicio = new EnvioCorreoIdempotente();   // una instancia por hilo (cada una con su EntityManager)
            inyectar(servicio, "em", em);
            inyectar(servicio, "email", email);
            servicio.enviar(clave, solicitud);
            return null;
        }));

        verify(email, times(1)).enviar(any(SolicitudNotificacionDTO.class));
        assertEquals(1, contar("select count(*) from notificacion_email_enviada where clave = :clave", "clave", clave));
    }

    @Test
    void dosSincronizacionesConElMismoSolicitudIdSeEjecutanUnaSolaVez() throws Exception {
        String solicitudId = UUID.randomUUID().toString();
        AtomicInteger ejecuciones = new AtomicInteger();

        ejecutarEnParalelo(() -> enTransaccion(em -> {
            var idempotente = new SincronizacionIdempotente();
            inyectar(idempotente, "em", em);
            idempotente.ejecutar(solicitudId, () -> {
                ejecuciones.incrementAndGet();
                pausa(400);
            });
            return null;
        }));

        assertEquals(1, ejecuciones.get());
        assertEquals(1, contar("select count(*) from sincronizacion_procesada where solicitud_id = :id",
                "id", solicitudId));
    }

    @Test
    void siLaSincronizacionFallaNoQuedaMarcadaYElReintentoSiSeEjecuta() {
        String solicitudId = UUID.randomUUID().toString();

        assertThrows(IllegalStateException.class, () -> enTransaccion(em -> {
            var idempotente = new SincronizacionIdempotente();
            inyectar(idempotente, "em", em);
            idempotente.ejecutar(solicitudId, () -> {
                throw new IllegalStateException("fallo del PMS");
            });
            return null;
        }));
        assertEquals(0, contar("select count(*) from sincronizacion_procesada where solicitud_id = :id",
                "id", solicitudId));

        AtomicInteger ejecuciones = new AtomicInteger();
        enTransaccion(em -> {
            var idempotente = new SincronizacionIdempotente();
            inyectar(idempotente, "em", em);
            idempotente.ejecutar(solicitudId, ejecuciones::incrementAndGet);
            return null;
        });
        assertEquals(1, ejecuciones.get());
        assertEquals(1, contar("select count(*) from sincronizacion_procesada where solicitud_id = :id",
                "id", solicitudId));
    }

    // ------------------------------------------------ utilidades

    private static void pausa(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Lanza la misma tarea en dos hilos que arrancan a la vez y espera a que terminen. */
    private static void ejecutarEnParalelo(Runnable tarea) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            Future<?> a = hilos.submit(() -> { esperar(salida); tarea.run(); });
            Future<?> b = hilos.submit(() -> { esperar(salida); tarea.run(); });
            salida.countDown();
            a.get(60, TimeUnit.SECONDS);
            b.get(60, TimeUnit.SECONDS);
        } finally {
            hilos.shutdownNow();
        }
    }

    private static void esperar(CountDownLatch salida) {
        try {
            salida.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}