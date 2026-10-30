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
        System.clearProperty("stayhub.canales.hosts-locales");
        System.clearProperty("stayhub.canales.permitir-http-local");
        System.clearProperty("stayhub.pms.wsdl");
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
    void aceptaMockHttpDeDockerSoloConDobleOptIn() {
        System.setProperty("stayhub.canales.hosts-permitidos", "ota-mock");
        System.setProperty("stayhub.canales.hosts-locales", "ota-mock");
        System.setProperty("stayhub.canales.permitir-http-local", "true");
        assertEquals("http://ota-mock:9080/api",
                new ConfiguracionCanales().urlPermitida("http://ota-mock:9080/api"));
    }

    @Test
    void conservaCompatibilidadConMockLocalExplicito() {
        System.setProperty("stayhub.canales.hosts-permitidos", "localhost");
        System.setProperty("stayhub.canales.permitir-http-local", "true");
        assertEquals("http://localhost:9091/api",
                new ConfiguracionCanales().urlPermitida("http://localhost:9091/api"));
    }

    @Test
    void rechazaMockHttpSinOptInExplicito() {
        System.setProperty("stayhub.canales.hosts-permitidos", "ota-mock");
        System.setProperty("stayhub.canales.permitir-http-local", "true");
        assertThrows(CanalExternoException.class,
                () -> new ConfiguracionCanales().urlPermitida("http://ota-mock:9080/api"));
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
        assertEquals(750, configuracion.timeoutConexionMs());
        assertEquals(1_500, configuracion.timeoutLecturaMs());
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
        System.setProperty("stayhub.canales.timeout.conexion.ms", "751");
        System.setProperty("stayhub.canales.timeout.lectura.ms", "1501");
        ConfiguracionCanales configuracion = new ConfiguracionCanales();
        assertThrows(CanalExternoException.class, configuracion::timeoutConexionMs);
        assertThrows(CanalExternoException.class, configuracion::timeoutLecturaMs);
    }

    @Test
    void derivaElEndpointSoapSinDescargarElWsdl() {
        System.setProperty("stayhub.canales.hosts-permitidos", "localhost");
        System.setProperty("stayhub.canales.permitir-http-local", "true");
        System.setProperty("stayhub.pms.wsdl", "http://localhost:9091/pms?wsdl");

        assertEquals("http://localhost:9091/pms", new ConfiguracionCanales().endpointPms());
    }
}
