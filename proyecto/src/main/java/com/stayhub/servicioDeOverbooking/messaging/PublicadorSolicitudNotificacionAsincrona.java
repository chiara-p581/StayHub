package com.stayhub.servicioDeOverbooking.messaging;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.Queue;

@ApplicationScoped
@JMSDestinationDefinition(
        name = PublicadorSolicitudNotificacionAsincrona.JNDI_COLA,
        interfaceName = "jakarta.jms.Queue",
        destinationName = "OverbookingNotificaciones"
)
public class PublicadorSolicitudNotificacionAsincrona {

    public static final String JNDI_COLA =
            "java:/jms/queue/OverbookingNotificaciones";

    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext contexto;

    @Resource(lookup = JNDI_COLA)
    private Queue cola;

    public void publicar(SolicitudNotificacionAsincrona solicitud) {
        contexto.createProducer().send(cola, solicitud);
    }
}