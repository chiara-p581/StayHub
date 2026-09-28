package com.stayhub.integracionpms.exception;

import jakarta.xml.ws.WebFault;

/**
 * Excepción declarada en las operaciones del servicio SOAP. Por estar en el
 * "throws" de los @WebMethod, JAX-WS la publica en el WSDL como
 * <wsdl:fault name="IntegracionPmsFault"> y, al lanzarla, responde un
 * soap:Fault con el detalle en ErrorIntegracionPms. Del lado cliente
 * (wsimport) llega como una excepción Java tipada.
 */
@WebFault(name = "errorIntegracionPms", targetNamespace = "http://soap.stayhub.com/integracion-pms")
public class IntegracionPmsFault extends Exception {

    private final ErrorIntegracionPms faultInfo;

    public IntegracionPmsFault(CodigoErrorIntegracionPms codigo, String mensaje) {
        this(mensaje, new ErrorIntegracionPms(codigo.name(), mensaje));
    }

    public IntegracionPmsFault(CodigoErrorIntegracionPms codigo, String mensaje, Throwable causa) {
        this(mensaje, new ErrorIntegracionPms(codigo.name(), mensaje), causa);
    }

    /** Constructores exigidos por la convención de JAX-WS para excepciones mapeadas a Fault. */
    public IntegracionPmsFault(String mensaje, ErrorIntegracionPms faultInfo) {
        super(mensaje);
        this.faultInfo = faultInfo;
    }

    public IntegracionPmsFault(String mensaje, ErrorIntegracionPms faultInfo, Throwable causa) {
        super(mensaje, causa);
        this.faultInfo = faultInfo;
    }

    public ErrorIntegracionPms getFaultInfo() { return faultInfo; }
}
