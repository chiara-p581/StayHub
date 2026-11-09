package com.stayhub.infraestructura.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CacheLecturasTest {
    @Test
    void unaRafagaConLaMismaClaveCargaUnaSolaVez() throws Exception {
        var cache = new CacheLecturas<String, Integer>(Duration.ofSeconds(10));
        var cargas = new AtomicInteger();
        var inicio = new CountDownLatch(1);
        Thread[] hilos = new Thread[20];
        for (int i = 0; i < hilos.length; i++) {
            hilos[i] = new Thread(() -> {
                try { inicio.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                assertEquals(7, cache.obtener("misma", () -> { cargas.incrementAndGet(); return 7; }));
            });
            hilos[i].start();
        }
        inicio.countDown();
        for (Thread hilo : hilos) hilo.join();
        assertEquals(1, cargas.get());
    }

    @Test
    void invalidarFuerzaUnaNuevaLectura() {
        var cache = new CacheLecturas<String, Integer>(Duration.ofSeconds(10));
        var cargas = new AtomicInteger();
        assertEquals(1, cache.obtener("clave", cargas::incrementAndGet));
        cache.invalidarTodo();
        assertEquals(2, cache.obtener("clave", cargas::incrementAndGet));
    }
}
