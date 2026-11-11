package com.stayhub.seguridad;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersistenciaSeguridadTest {
    @Test void limitadorUsaUpsertAtomicoCuandoExisteJpa() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(contains("ON CONFLICT"))).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(3);
        LimitadorSolicitudes limitador = new LimitadorSolicitudes();
        limitador.em = em;

        assertTrue(limitador.permitir("login:hash", 3, 1_000));
        verify(em).createNativeQuery(contains("RETURNING cantidad"));
    }

    @Test void auditoriaInsertaTodosLosCamposSinGuardarCuerpos() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(contains("evento_auditoria_seguridad"))).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        AuditoriaPersistente auditoria = new AuditoriaPersistente();
        auditoria.em = em;

        auditoria.guardar(Instant.EPOCH, "corr-12345678", "1", "ADMIN",
                "LOGIN_EXITOSO", "EXITO", "/api/usuarios/login", "abcd1234", "HTTP_200");

        verify(query).executeUpdate();
        verify(query, times(9)).setParameter(anyInt(), any());
    }
}
