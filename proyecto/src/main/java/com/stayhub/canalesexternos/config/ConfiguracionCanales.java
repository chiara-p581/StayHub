package com.stayhub.canalesexternos.config;
import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.exception.*;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class ConfiguracionCanales {
    private static final long TIMEOUT_CONEXION_PREDETERMINADO_MS = 5_000;
    private static final long TIMEOUT_LECTURA_PREDETERMINADO_MS = 15_000;

    public String urlOta(Canal canal) { return requerida("stayhub.ota." + canal.name().toLowerCase() + ".url"); }
    public String tokenOta(Canal canal) { return opcional("stayhub.ota." + canal.name().toLowerCase() + ".token"); }
    public String wsdlPms() { return requerida("stayhub.pms.wsdl"); }
    public long timeoutConexionMs() { return enteroPositivo("stayhub.canales.timeout.conexion.ms", TIMEOUT_CONEXION_PREDETERMINADO_MS); }
    public long timeoutLecturaMs() { return enteroPositivo("stayhub.canales.timeout.lectura.ms", TIMEOUT_LECTURA_PREDETERMINADO_MS); }
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

    private long enteroPositivo(String clave, long predeterminado) {
        String valor = opcional(clave);
        if (valor == null || valor.isBlank()) return predeterminado;
        try {
            long numero = Long.parseLong(valor);
            if (numero < 1) throw new NumberFormatException();
            return numero;
        } catch (NumberFormatException ex) {
            throw new CanalExternoException(CodigoErrorCanal.SOLICITUD_INVALIDA,
                    "La configuración " + clave + " debe ser un entero positivo", ex);
        }
    }
}
