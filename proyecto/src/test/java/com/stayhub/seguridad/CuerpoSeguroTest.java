package com.stayhub.seguridad;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuerpoSeguroTest {
    byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test void xmlValidoSeAcepta(){assertDoesNotThrow(()->CuerpoSeguro.xml(bytes("<Envelope xmlns='http://schemas.xmlsoap.org/soap/envelope/'><Body><consultarReserva><id>1</id></consultarReserva></Body></Envelope>")));}
    @Test void xmlExternoNuncaSeResuelve(){assertThrows(SeguridadException.class,()->CuerpoSeguro.xml(bytes("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>")));}
    @Test void xmlMuyProfundoSeRechaza(){assertThrows(SeguridadException.class,()->CuerpoSeguro.xml(bytes("<x>".repeat(33)+"</x>".repeat(33))));}
    @Test void jsonMuyProfundoSeRechaza(){assertThrows(SeguridadException.class,()->CuerpoSeguro.json(bytes("[".repeat(21)+"0"+"]".repeat(21))));}
    @Test void exponentesGigantesSeRechazan(){assertThrows(SeguridadException.class,()->CuerpoSeguro.json(bytes("{\"monto\":1e1000000000}")));}
    @Test void textoConLlavesNoCuentaComoNesting(){assertDoesNotThrow(()->CuerpoSeguro.json(bytes("{\"mensaje\":\""+"[".repeat(100)+"\"}")));}
    @Test void tamanoAcotadoIncluyeRequestsSinContentLength(){assertThrows(SeguridadException.class,()->CuerpoSeguro.leer(new ByteArrayInputStream(new byte[65537])));}
    @Test void rangoGiganteSeRechaza(){assertThrows(SeguridadException.class,()->Limites.rango(LocalDate.now(),LocalDate.now().plusDays(367)));}
    @Test void ventanaDeRateSeRenueva(){var limiter=new LimitadorSolicitudes();assertTrue(limiter.permitir("usuario",1,0));assertFalse(limiter.permitir("usuario",1,1));assertTrue(limiter.permitir("usuario",1,60000));}
    @Test void tokenDeOtraSesionNoAutoriza(){var a=IdentidadFixture.sesion(null,null);var b=IdentidadFixture.sesion(null,null);assertFalse(Csrf.valido(b,Csrf.token(a)));assertTrue(Csrf.valido(a,Csrf.token(a)));}
}
