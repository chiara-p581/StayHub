package com.stayhub.canalesexternos.config;
import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.exception.*;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class ConfiguracionCanales {
    public String urlOta(Canal canal) { return requerida("stayhub.ota." + canal.name().toLowerCase() + ".url"); }
    public String tokenOta(Canal canal) { return opcional("stayhub.ota." + canal.name().toLowerCase() + ".token"); }
    public String wsdlPms() { return requerida("stayhub.pms.wsdl"); }
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
}
