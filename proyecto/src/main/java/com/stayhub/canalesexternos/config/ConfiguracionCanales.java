package com.stayhub.canalesexternos.config;
import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.exception.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
@ApplicationScoped
public class ConfiguracionCanales {
    private static final long TIMEOUT_CONEXION_PREDETERMINADO_MS = 3_000;
    private static final long TIMEOUT_LECTURA_PREDETERMINADO_MS = 10_000;

    public String urlOta(Canal canal) { return urlPermitida(requerida("stayhub.ota." + canal.name().toLowerCase() + ".url")); }
    public String tokenOta(Canal canal) { return opcional("stayhub.ota." + canal.name().toLowerCase() + ".token"); }
    public String wsdlPms() { return urlPermitida(requerida("stayhub.pms.wsdl")); }
    public long timeoutConexionMs() { return enteroAcotado("stayhub.canales.timeout.conexion.ms", TIMEOUT_CONEXION_PREDETERMINADO_MS, 3_000); }
    public long timeoutLecturaMs() { return enteroAcotado("stayhub.canales.timeout.lectura.ms", TIMEOUT_LECTURA_PREDETERMINADO_MS, 10_000); }
    private String requerida(String clave) {
        String valor = opcional(clave);
        if (valor == null || valor.isBlank()) throw new CanalExternoException(
                CodigoErrorCanal.DEPENDENCIA_NO_DISPONIBLE, "Falta configurar " + clave);
        return valor;
    }
    private String opcional(String clave) {
        String valor = System.getProperty(clave);
        return valor != null ? valor : System.getenv(clave.toUpperCase().replace('.', '_'));
    }

    private long enteroAcotado(String clave, long predeterminado, long maximo) {
        String valor = opcional(clave);
        if (valor == null || valor.isBlank()) return predeterminado;
        try {
            long numero = Long.parseLong(valor);
            if (numero < 1 || numero > maximo) throw new NumberFormatException();
            return numero;
        } catch (NumberFormatException ex) {
            throw new CanalExternoException(CodigoErrorCanal.SOLICITUD_INVALIDA,
                    "La configuración " + clave + " debe estar entre 1 y " + maximo + " ms", ex);
        }
    }

    String urlPermitida(String valor) {
        try {
            URI uri = URI.create(valor);
            String host = uri.getHost();
            boolean localHttp = Boolean.parseBoolean(opcional("stayhub.canales.permitir-http-local"));
            boolean hostLocalPermitido = host != null && hostsLocales().contains(host.toLowerCase());
            boolean httpLocalAutorizado = localHttp && "http".equalsIgnoreCase(uri.getScheme())
                    && (esLocal(host) || hostLocalPermitido);
            boolean esquemaValido = "https".equalsIgnoreCase(uri.getScheme())
                    || httpLocalAutorizado;
            if (!esquemaValido || host == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || (hostBloqueado(host) && !httpLocalAutorizado)
                    || !hostsPermitidos().contains(host.toLowerCase())) {
                throw new IllegalArgumentException();
            }
            if (!httpLocalAutorizado) {
                validarDirecciones(host);
            }
            return uri.toString();
        } catch (RuntimeException ex) {
            if (ex instanceof CanalExternoException canalExternoException) throw canalExternoException;
            throw new CanalExternoException(CodigoErrorCanal.SOLICITUD_INVALIDA,
                    "La URL de integración no pertenece a un host HTTPS permitido", ex);
        }
    }

    void validarDirecciones(String host) {
        try {
            InetAddress[] direcciones = resolver(host);
            if (direcciones.length == 0 || Arrays.stream(direcciones).anyMatch(this::direccionBloqueada)) {
                throw new IllegalArgumentException("El host resuelve a una red no permitida");
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("No se pudo resolver el host", ex);
        }
    }

    InetAddress[] resolver(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    private boolean direccionBloqueada(InetAddress direccion) {
        byte[] bytes = direccion.getAddress();
        boolean ipv6Privada = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        return direccion.isAnyLocalAddress() || direccion.isLoopbackAddress()
                || direccion.isLinkLocalAddress() || direccion.isSiteLocalAddress()
                || direccion.isMulticastAddress() || ipv6Privada;
    }

    private Set<String> hostsPermitidos() {
        String valor = requerida("stayhub.canales.hosts-permitidos");
        return Arrays.stream(valor.split(",")).map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> hostsLocales() {
        String valor = opcional("stayhub.canales.hosts-locales");
        if (valor == null || valor.isBlank()) return Set.of();
        return Arrays.stream(valor.split(",")).map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private boolean esLocal(String host) {
        return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
    }

    private boolean hostBloqueado(String host) {
        String h = host.toLowerCase();
        return esLocal(h) || h.endsWith(".local") || h.equals("169.254.169.254")
                || h.startsWith("10.") || h.startsWith("192.168.") || h.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")
                || h.startsWith("169.254.") || h.equals("0.0.0.0");
    }
}
