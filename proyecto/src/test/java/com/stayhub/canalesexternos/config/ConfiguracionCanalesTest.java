package com.stayhub.canalesexternos.config;

import com.stayhub.canalesexternos.exception.CanalExternoException;
import com.stayhub.canalesexternos.exception.CodigoErrorCanal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionCanalesTest {

    @AfterEach
    void limpiar() {
        System.clearProperty("stayhub.canales.timeout.conexion.ms");
        System.clearProperty("stayhub.canales.timeout.lectura.ms");
    }

    @Test
    void usaTimeoutsPredeterminadosSeguros() {
        ConfiguracionCanales configuracion = new ConfiguracionCanales();
        assertEquals(5_000, configuracion.timeoutConexionMs());
        assertEquals(15_000, configuracion.timeoutLecturaMs());
    }

    @Test
    void rechazaTimeoutNoPositivo() {
        System.setProperty("stayhub.canales.timeout.conexion.ms", "0");
        ConfiguracionCanales configuracion = new ConfiguracionCanales();

        CanalExternoException error = assertThrows(CanalExternoException.class,
                configuracion::timeoutConexionMs);
        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, error.getCodigo());
    }
}
