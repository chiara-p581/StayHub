package com.stayhub.integracionpms.exception;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/**
 * "Fault bean": lo que viaja dentro de soap:Fault/detail. Es el equivalente
 * al <af:error codigo="AF-404"/> del ejemplo de la Clase 9.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "errorIntegracionPms", propOrder = {"codigo", "mensaje"})
public class ErrorIntegracionPms {

    private String codigo;
    private String mensaje;

    public ErrorIntegracionPms() { }

    public ErrorIntegracionPms(String codigo, String mensaje) {
        this.codigo = codigo;
        this.mensaje = mensaje;
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}
