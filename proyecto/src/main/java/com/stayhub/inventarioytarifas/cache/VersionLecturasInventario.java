package com.stayhub.inventarioytarifas.cache;

import java.util.concurrent.atomic.AtomicLong;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/** Marca cambios de cupo para que las consultas públicas no reutilicen una vista anterior. */
public final class VersionLecturasInventario {
    private static final AtomicLong VERSION = new AtomicLong();

    private VersionLecturasInventario() { }

    public static long actual() {
        return VERSION.get();
    }

    public static void invalidar() {
        try {
            var registro = (TransactionSynchronizationRegistry) InitialContext.doLookup(
                    "java:comp/TransactionSynchronizationRegistry");
            if (registro.getTransactionKey() != null) {
                registro.registerInterposedSynchronization(new Synchronization() {
                    public void beforeCompletion() { }
                    public void afterCompletion(int estado) {
                        if (estado == Status.STATUS_COMMITTED) VERSION.incrementAndGet();
                    }
                });
                return;
            }
        } catch (NamingException ex) {
            // Ejecución unitaria sin contenedor: la mutación ya es visible.
        }
        VERSION.incrementAndGet();
    }
}
