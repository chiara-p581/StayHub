package com.stayhub.notificaciones.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Registra metadatos operativos sin incluir payloads ni datos sensibles. */
public final class FalloJms {
    private static final Logger LOG = Logger.getLogger(FalloJms.class.getName());

    private FalloJms() { }

    public static IllegalStateException reintentar(Message mensaje, String consumidor, Exception causa) {
        String id = "desconocido";
        String correlacion = "sin-correlacion";
        int entrega = -1;
        try {
            if (mensaje.getJMSMessageID() != null) id = mensaje.getJMSMessageID();
            if (mensaje.getJMSCorrelationID() != null) correlacion = mensaje.getJMSCorrelationID();
            entrega = mensaje.getIntProperty("JMSXDeliveryCount");
        } catch (JMSException ignorada) {
            // El broker volverá a entregar igualmente; no ocultamos la causa original.
        }
        String codigo = causa.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_$]", "_");
        LOG.log(Level.WARNING,
                "jms_failure consumidor={0} messageId={1} correlationId={2} delivery={3} cause={4}",
                new Object[]{seguro(consumidor), seguro(id), seguro(correlacion), entrega, codigo});
        // El contenedor imprime la cadena de causas al revertir un MDB. No
        // adjuntar la excepción original: su mensaje puede contener payloads o secretos.
        return new IllegalStateException("Fallo JMS recuperable en " + consumidor + " [" + codigo + "]");
    }

    private static String seguro(String valor) {
        String saneado = valor.replaceAll("[^A-Za-z0-9:._-]", "_");
        return saneado.substring(0, Math.min(saneado.length(), 128));
    }
}
