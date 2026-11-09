package com.stayhub.infraestructura.cache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Caché breve para consultas públicas repetitivas.
 *
 * <p>Al vencer una entrada, un único request la actualiza y los demás reciben
 * el último valor válido. Esto evita estampidas contra la base remota. El
 * tamaño y las cargas simultáneas están acotados para que parámetros elegidos
 * por un cliente no consuman memoria ni conexiones sin límite.</p>
 */
public final class CacheLecturas<K, V> {
    private static final int MAX_ENTRADAS = 256;
    private static final Semaphore CARGAS = new Semaphore(8, true);

    private final long ttlMillis;
    private final Map<K, Entrada<V>> entradas = new ConcurrentHashMap<>();
    private final Map<K, Object> monitores = new ConcurrentHashMap<>();

    public CacheLecturas(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("El TTL debe ser positivo");
        }
        this.ttlMillis = ttl.toMillis();
    }

    public V obtener(K clave, Supplier<V> cargador) {
        if (clave == null || cargador == null) throw new IllegalArgumentException("Clave y cargador son obligatorios");
        long ahora = System.currentTimeMillis();
        Entrada<V> entrada = entradas.get(clave);
        if (entrada == null) return cargaInicial(clave, cargador);
        if (ahora < entrada.venceEn) return entrada.valor;

        if (entrada.actualizando.compareAndSet(false, true)) {
            try {
                V valor = cargar(cargador);
                guardar(clave, valor);
                return valor;
            } finally {
                entrada.actualizando.set(false);
            }
        }
        return entrada.valor;
    }

    public void invalidarTodo() {
        entradas.clear();
    }

    private V cargaInicial(K clave, Supplier<V> cargador) {
        Object monitor = monitores.computeIfAbsent(clave, ignorada -> new Object());
        try {
            synchronized (monitor) {
                Entrada<V> existente = entradas.get(clave);
                if (existente != null) return existente.valor;
                V valor = cargar(cargador);
                guardar(clave, valor);
                return valor;
            }
        } finally {
            monitores.remove(clave, monitor);
        }
    }

    private V cargar(Supplier<V> cargador) {
        boolean adquirido = false;
        try {
            CARGAS.acquire();
            adquirido = true;
            return cargador.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Carga de lectura interrumpida", ex);
        } finally {
            if (adquirido) CARGAS.release();
        }
    }

    private void guardar(K clave, V valor) {
        if (entradas.size() >= MAX_ENTRADAS && !entradas.containsKey(clave)) entradas.clear();
        entradas.put(clave, new Entrada<>(valor, System.currentTimeMillis() + ttlMillis));
    }

    private static final class Entrada<V> {
        private final V valor;
        private final long venceEn;
        private final AtomicBoolean actualizando = new AtomicBoolean();

        private Entrada(V valor, long venceEn) {
            this.valor = valor;
            this.venceEn = venceEn;
        }
    }
}
