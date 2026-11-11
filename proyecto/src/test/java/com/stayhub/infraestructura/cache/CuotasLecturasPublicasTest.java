package com.stayhub.infraestructura.cache;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuotasLecturasPublicasTest {
    private CuotasLecturasPublicas nodo(ReservaCuotaPublica reserva) throws Exception {
        var nodo = new CuotasLecturasPublicas();
        var campo = CuotasLecturasPublicas.class.getDeclaredField("reservas");
        campo.setAccessible(true); campo.set(nodo, reserva); return nodo;
    }

    @Test void dosNodosCompartenElLimiteSinSobrepasarloAunqueElUltimoBloqueSeaParcial() throws Exception {
        var reserva = mock(ReservaCuotaPublica.class);
        var total = new AtomicInteger();
        when(reserva.reservar(anyString(), eq(6000), eq(512), anyLong())).thenAnswer(call -> {
            int antes = total.getAndAdd(512);
            return new ReservaCuotaPublica.Bloque(Math.max(0, Math.min(512, 6000 - antes)), 60_000);
        });
        var a = nodo(reserva); var b = nodo(reserva); int aceptadas = 0;
        for (int i = 0; i < 7000; i++) if ((i % 2 == 0 ? a : b).permitir("ip", 6000, 1000)) aceptadas++;
        assertEquals(6000, aceptadas);
        assertFalse(a.permitir("ip", 6000, 1000));
        assertFalse(b.permitir("ip", 6000, 1000));
        verify(reserva, atMost(14)).reservar(anyString(), eq(6000), eq(512), anyLong());
    }

    @Test void reiniciarNoDevuelveAlContadorCompartidoLosPermisosReservados() throws Exception {
        var reserva = mock(ReservaCuotaPublica.class);
        when(reserva.reservar(anyString(), eq(512), eq(512), anyLong()))
                .thenReturn(new ReservaCuotaPublica.Bloque(512, 60_000), new ReservaCuotaPublica.Bloque(0, 60_000));
        assertTrue(nodo(reserva).permitir("ip", 512, 1000));
        assertFalse(nodo(reserva).permitir("ip", 512, 1000));
    }

    @Test void reponerNoBloqueaSolicitudesQueTodaviaTienenSaldo() throws Exception {
        var reserva = mock(ReservaCuotaPublica.class);
        var iniciada = new java.util.concurrent.CountDownLatch(1);
        var liberar = new java.util.concurrent.CountDownLatch(1);
        var llamadas = new AtomicInteger();
        when(reserva.reservar(anyString(), anyInt(), eq(512), anyLong())).thenAnswer(call -> {
            if (llamadas.incrementAndGet() == 2) { iniciada.countDown(); liberar.await(); }
            return new ReservaCuotaPublica.Bloque(512, 60_000);
        });
        var nodo = nodo(reserva);
        for (int i = 0; i < 255; i++) assertTrue(nodo.permitir("ip", 6000, 1000));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var reposicion = executor.submit(() -> nodo.permitir("ip", 6000, 1000));
            assertTrue(iniciada.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(executor.submit(() -> nodo.permitir("ip", 6000, 1000)).get(1, java.util.concurrent.TimeUnit.SECONDS));
            liberar.countDown();
            assertTrue(reposicion.get(2, java.util.concurrent.TimeUnit.SECONDS));
        } finally { liberar.countDown(); executor.shutdownNow(); }
    }

    @Test void fallaDePersistenciaNoConcedeCuotaLocal() throws Exception {
        var reserva = mock(ReservaCuotaPublica.class);
        when(reserva.reservar(anyString(), anyInt(), anyInt(), anyLong())).thenThrow(new IllegalStateException("base no disponible"));
        assertThrows(IllegalStateException.class, () -> nodo(reserva).permitir("ip", 6000, 1000));
    }
}
