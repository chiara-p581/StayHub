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

    /**
     * El mensaje puede llevar el token de recuperación. Como el link vence en
     * 1 hora, el mensaje también: si no se procesó en ese tiempo, el broker lo
     * descarta y el token no queda guardado en la cola (minimizar datos sensibles).
     */
    static final long VIDA_MENSAJE_MS = 60 * 60 * 1000L;

    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext contexto;

    @Resource(lookup = JNDI_COLA)
    private Queue cola;

    public void publicar(EventoPassword evento) {
        contexto.createProducer().setTimeToLive(VIDA_MENSAJE_MS).send(cola, evento);
    }
}