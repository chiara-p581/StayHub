package com.stayhub.usuarios.messaging;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.Queue;

/** Cola: cada solicitud de recuperación es una tarea puntual para un único consumidor. */
@ApplicationScoped
@JMSDestinationDefinition(
        name = PublicadorEventoPassword.JNDI_COLA,
        interfaceName = "jakarta.jms.Queue",
        destinationName = "UsuariosPassword")
public class PublicadorEventoPassword {

    public static final String JNDI_COLA = "java:/jms/queue/UsuariosPassword";

    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext contexto;

    @Resource(lookup = JNDI_COLA)
    private Queue cola;

    public void publicar(EventoPassword evento) {
        contexto.createProducer().send(cola, evento);
    }
}