package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.model.SincronizacionProcesada;
import jakarta.jms.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConsumidorSincronizacionDuplicadoTest {

    private final LocalDate desde = LocalDate.now().plusDays(1);
    private final LocalDate hasta = desde.plusDays(1);
    private final Set<String> tabla = new HashSet<>(); // simula sincronizacion_procesada

    private ProcesadorSincronizacion procesador;
    private ConsumidorSincronizacion consumidor;

    private void inyectar(Object o, String nombre, Object valor) throws Exception {
        var f = o.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        f.set(o, valor);
    }

    @BeforeEach
    void preparar() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query consulta = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(consulta);
        when(consulta.setParameter(eq("clave"), any())).thenReturn(consulta);
        when(em.find(eq(SincronizacionProcesada.class), anyString()))
                .thenAnswer(i -> tabla.contains(i.<String>getArgument(1)) ? new SincronizacionProcesada(i.getArgument(1)) : null);
        doAnswer(i -> {
            tabla.add(((SincronizacionProcesada) i.getArgument(0)).getSolicitudId());
            return null;
        }).when(em).persist(any(SincronizacionProcesada.class));

        var idempotente = new SincronizacionIdempotente();
        inyectar(idempotente, "em", em);

        procesador = mock(ProcesadorSincronizacion.class);
        consumidor = new ConsumidorSincronizacion();
        inyectar(consumidor, "procesador", procesador);
        inyectar(consumidor, "idempotente", idempotente);
    }

    private Message mensajeOta(String id) throws Exception {
        Message m = mock(Message.class);
        when(m.getBody(SolicitudSincronizacion.class))
                .thenReturn(SolicitudSincronizacion.paraOta(id, 1L, Canal.BOOKING, desde, hasta));
        return m;
    }

    @Test
    void mensajeDuplicadoSincronizaUnaSolaVez() throws Exception {
        Message m = mensajeOta("sol-1");

        consumidor.onMessage(m);
        consumidor.onMessage(m); // reentrega del broker

        verify(procesador, times(1)).sincronizarOta(1L, Canal.BOOKING, desde, hasta);
    }

    @Test
    void solicitudesDistintasSeProcesanCadaUna() throws Exception {
        consumidor.onMessage(mensajeOta("sol-1"));
        consumidor.onMessage(mensajeOta("sol-2"));

        verify(procesador, times(2)).sincronizarOta(1L, Canal.BOOKING, desde, hasta);
    }

    @Test
    void siFallaNoSeMarcaYElReintentoSiSeEjecuta() throws Exception {
        doThrow(new IllegalStateException("timeout")).doNothing()
                .when(procesador).sincronizarOta(1L, Canal.BOOKING, desde, hasta);
        Message m = mensajeOta("sol-3");

        assertThrows(IllegalStateException.class, () -> consumidor.onMessage(m));
        consumidor.onMessage(m); // reintento: debe ejecutarse de nuevo
        consumidor.onMessage(m); // duplicado posterior: ya no

        verify(procesador, times(2)).sincronizarOta(1L, Canal.BOOKING, desde, hasta);
    }

    @Test
    void duplicadoPmsTambienSeIgnora() throws Exception {
        Message m = mock(Message.class);
        when(m.getBody(SolicitudSincronizacion.class))
                .thenReturn(SolicitudSincronizacion.paraPms("sol-4", 1L, desde, hasta));

        consumidor.onMessage(m);
        consumidor.onMessage(m);

        verify(procesador, times(1)).sincronizarPms(1L, desde, hasta);
    }
}