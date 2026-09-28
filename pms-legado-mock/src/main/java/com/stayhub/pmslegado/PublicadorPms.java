package com.stayhub.pmslegado;

import jakarta.xml.ws.Endpoint;

/**
 * Publica el PMS simulado de forma standalone (Opción A de la Clase 9:
 * Endpoint.publish), fuera de WildFly.
 *
 * WSDL: http://localhost:9091/PmsLegacyService?wsdl
 * (es la URL que StayHub lee de la system property stayhub.pms.wsdl)
 */
public class PublicadorPms {

    public static void main(String[] args) {
        String url = System.getProperty("pms.url", "http://localhost:9091/PmsLegacyService");
        Endpoint.publish(url, new PmsLegacyServiceImpl());
        System.out.println("PMS legado (simulado) escuchando en " + url);
        System.out.println("WSDL: " + url + "?wsdl");
        System.out.println("Ctrl+C para detenerlo.");
    }
}
