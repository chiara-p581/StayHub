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
        System.clearProperty("stayhub.canales.hosts-permitidos");
        System.clearProperty("stayhub.canales.permitir-http-local");
    }

    @Test
    void aceptaSoloHttpsDeHostsExplicitamentePermitidos() {
        System.setProperty("stayhub.canales.hosts-permitidos", "api.booking.example,pms.example");
        ConfiguracionCanales configuracion = new ConfiguracionCanales();
        assertEquals("https://api.booking.example/v1", configuracion.urlPermitida("https://api.booking.example/v1"));
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("https://169.254.169.254/latest/meta-data"));
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("https://otro.example/v1"));
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("http://api.booking.example/v1"));
    }

    @Test
    void usaTimeoutsPredeterminadosSeguros() {
        ConfiguracionCanales configuracion = new ConfiguracionCanales();
        assertEquals(3_000, configuracion.timeoutConexionMs());
        assertEquals(10_000, configuracion.timeoutLecturaMs());
    }

    @Test
    void rechazaTimeoutNoPositivo() {
        System.setProperty("stayhub.canales.timeout.conexion.ms", "0");
        ConfiguracionCanales configuracion = new ConfiguracionCanales();

        CanalExternoException error = assertThrows(CanalExternoException.class,
                configuracion::timeoutConexionMs);
        assertEquals(CodigoErrorCanal.SOLICITUD_INVALIDA, error.getCodigo());
    }

    @Test
    void rechazaTimeoutsMayoresAlContrato() {
        System.setProperty("stayhub.canales.timeout.conexion.ms", "3001");
        System.setProperty("stayhub.canales.timeout.lectura.ms", "10001");
        ConfiguracionCanales configuracion = new ConfiguracionCanales();
        assertThrows(CanalExternoException.class, configuracion::timeoutConexionMs);
        assertThrows(CanalExternoException.class, configuracion::timeoutLecturaMs);
    }}
