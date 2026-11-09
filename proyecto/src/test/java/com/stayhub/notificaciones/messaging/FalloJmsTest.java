package com.stayhub.notificaciones.messaging;

import jakarta.jms.Message;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FalloJmsTest {
    @Test void conservaCorrelacionYEntregaSinFiltrarLaCausaOriginalNiPermitirInyeccionDeLog() throws Exception {
        Message mensaje = mock(Message.class);
        when(mensaje.getJMSMessageID()).thenReturn("ID:prueba-93");
        when(mensaje.getJMSCorrelationID()).thenReturn("corr-93\nlinea-forjada");
        when(mensaje.getIntProperty("JMSXDeliveryCount")).thenReturn(5);
        List<String> lineas = new ArrayList<>();
        Logger logger = Logger.getLogger(FalloJms.class.getName());
        Handler handler = new Handler() {
            public void publish(LogRecord record) { lineas.add(MessageFormat.format(record.getMessage(), record.getParameters())); }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        try {
            var fallo = FalloJms.reintentar(mensaje, "prueba-93", new IllegalArgumentException("password=SECRETO payload=PRIVADO"));
            assertNull(fallo.getCause(), "El contenedor no debe poder imprimir el mensaje privado de la causa");
            assertFalse(fallo.toString().contains("SECRETO"));
            String linea = String.join("", lineas);
            assertTrue(linea.contains("messageId=ID:prueba-93"));
            assertTrue(linea.contains("correlationId=corr-93_linea-forjada"));
            assertTrue(linea.contains("delivery=5"));
            assertTrue(linea.contains("cause=IllegalArgumentException"));
            assertFalse(linea.contains("SECRETO"));
            assertFalse(linea.contains("PRIVADO"));
            assertFalse(linea.contains("\n"));
        } finally { logger.removeHandler(handler); }
    }
}
