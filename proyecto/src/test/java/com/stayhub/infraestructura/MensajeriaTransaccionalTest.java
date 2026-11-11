package com.stayhub.infraestructura;

import com.stayhub.canalesexternos.messaging.PublicadorSincronizacion;
import com.stayhub.pagos.messaging.PublicadorEventoPago;
import com.stayhub.servicioDeOverbooking.messaging.PublicadorSolicitudNotificacionAsincrona;
import com.stayhub.usuarios.messaging.PublicadorEventoPassword;
import com.stayhub.usuarios.messaging.PublicadorEventoUsuario;
import jakarta.jms.JMSConnectionFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MensajeriaTransaccionalTest {
    private static String archivo(String ruta) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), "..", ruta).normalize());
    }

    @Test void todosLosProductoresUsanLaFabricaXa() throws Exception {
        for (Class<?> tipo : List.of(PublicadorEventoPago.class, PublicadorEventoUsuario.class,
                PublicadorEventoPassword.class, PublicadorSolicitudNotificacionAsincrona.class,
                PublicadorSincronizacion.class)) {
            var campo=tipo.getDeclaredField("contexto");
            var xa=campo.getAnnotation(JMSConnectionFactory.class);
            assertNotNull(xa,tipo.getSimpleName());
            assertEquals("java:/JmsXA",xa.value(),tipo.getSimpleName());
        }
    }

    @Test void dockerUsaPostgresXaYTransaccionesPreparadas() throws Exception {
        String wildfly=archivo("docker/wildfly/configure.cli");
        String compose=archivo("compose.yaml");
        assertTrue(wildfly.contains("xa-data-source=StayHubDS"));
        assertTrue(wildfly.contains("PGXADataSource"));
        assertTrue(compose.contains("max_prepared_transactions=100"));
    }

    @Test void dlqCubreTodosLosDestinosYAcotaLosReintentos() throws Exception {
        String cli=archivo("config/notificaciones-reintentos.cli");
        for (String destino : List.of("UsuariosEventos", "PagosEventos", "UsuariosPassword",
                "OverbookingNotificaciones", "CanalesExternosSincronizaciones")) {
            assertTrue(cli.contains(destino),destino);
        }
        assertEquals(10, cli.split("max-delivery-attempts=5",-1).length-1);
        assertFalse(cli.contains("StayHubEmailDLQ"));
        assertTrue(cli.contains("StayHubDLQ"));
    }
}
