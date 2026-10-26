package com.stayhub.canalesexternos.config;

import com.stayhub.canalesexternos.exception.CanalExternoException;
import com.stayhub.canalesexternos.exception.CodigoErrorCanal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionCanalesTest {

    @AfterEach
    void limpiar() {
        System.clearProperty("stayhub.canales.timeout.conexion.ms");
        System.clearProperty("stayhub.canales.timeout.lectura.ms");
        System.clearProperty("stayhub.canales.hosts-permitidos");
        System.clearProperty("stayhub.canales.permitir-http-local");
        System.clearProperty("stayhub.pms.wsdl");
        System.clearProperty("stayhub.pms.endpoint");
        System.clearProperty("stayhub.ota.booking.token");
    }

    @Test
    void aceptaSoloHttpsDeHostsExplicitamentePermitidos() {
        System.setProperty("stayhub.canales.hosts-permitidos", "api.booking.example,pms.example");
        ConfiguracionCanales configuracion = configuracionConDns("8.8.8.8");
        assertEquals("https://api.booking.example/v1", configuracion.urlPermitida("https://api.booking.example/v1"));
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("https://169.254.169.254/latest/meta-data"));
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("https://otro.example/v1"));
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("http://api.booking.example/v1"));
    }

    @Test
    void rechazaHostPermitidoSiDnsResuelveARedPrivada() {
        System.setProperty("stayhub.canales.hosts-permitidos", "api.booking.example");
        ConfiguracionCanales configuracion = configuracionConDns("127.0.0.1");
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("https://api.booking.example/v1"));
    }

    @Test
    void rechazaRutasNormalizablesYUrlsExcesivas() {
        System.setProperty("stayhub.canales.hosts-permitidos", "api.booking.example");
        ConfiguracionCanales configuracion = configuracionConDns("8.8.8.8");
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("https://api.booking.example/a/../admin"));
        assertThrows(CanalExternoException.class,
                () -> configuracion.urlPermitida("https://api.booking.example/" + "a".repeat(2_100)));
    }

    @Test
    void endpointPmsNoPuedeCambiarDeOrigenRespectoDelWsdl() {
        System.setProperty("stayhub.canales.hosts-permitidos", "pms.example,otro.example");
        System.setProperty("stayhub.pms.wsdl", "https://pms.example/Pms?wsdl");
        ConfiguracionCanales configuracion = configuracionConDns("8.8.8.8");
        assertEquals("https://pms.example/Pms", configuracion.endpointPms());
        System.setProperty("stayhub.pms.endpoint", "https://otro.example/Pms");
        assertThrows(CanalExternoException.class, configuracion::endpointPms);
    }

    @Test
    void credencialOtaEsObligatoriaYNoAdmiteControles() {
        ConfiguracionCanales configuracion = new ConfiguracionCanales();
        assertThrows(CanalExternoException.class, () -> configuracion.tokenOta(com.stayhub.canalesexternos.dto.Canal.BOOKING));
        System.setProperty("stayhub.ota.booking.token", "token\r\ninyectado");
        assertThrows(CanalExternoException.class, () -> configuracion.tokenOta(com.stayhub.canalesexternos.dto.Canal.BOOKING));
    }

    private ConfiguracionCanales configuracionConDns(String ip) {
        return new ConfiguracionCanales() {
            @Override InetAddress[] resolver(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName(ip)};
            }
        };
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
