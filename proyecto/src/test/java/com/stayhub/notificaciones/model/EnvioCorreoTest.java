package com.stayhub.notificaciones.model;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvioCorreoTest {

    private static Object campo(EnvioCorreo e, String nombre) throws Exception {
        var f = EnvioCorreo.class.getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(e);
    }

    @Test
    void guardaLaClaveYMarcaLaFechaDeEnvio() throws Exception {
        OffsetDateTime antes = OffsetDateTime.now();

        EnvioCorreo e = new EnvioCorreo("pago:42:ana@test.com");

        assertEquals("pago:42:ana@test.com", campo(e, "clave"));
        OffsetDateTime fecha = (OffsetDateTime) campo(e, "fecha");
        assertFalse(fecha.isBefore(antes));
        assertFalse(fecha.isAfter(OffsetDateTime.now()));
    }

    @Test
    void permiteClaveNulaSinFallarEnElConstructor() throws Exception {
        EnvioCorreo e = new EnvioCorreo(null);
        assertNull(campo(e, "clave"));
        assertNotNull(campo(e, "fecha"));
    }
}
