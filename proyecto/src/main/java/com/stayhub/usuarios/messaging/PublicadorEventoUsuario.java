package com.stayhub.usuarios.messaging;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.Topic;

/**
 * Patrón Publish-Subscribe, mismo criterio que PublicadorEventoPago en
 * el componente Pagos: usamos tópico (no cola) porque el registro de un
 * usuario es un evento de interés general, no una tarea de un único
 * consumidor.
 */
@ApplicationScoped
@JMSDestinationDefinition(
        name = PublicadorEventoUsuario.JNDI_TOPICO,
        interfaceName = "jakarta.jms.Topic",
        destinationName = "UsuariosEventos")
public class PublicadorEventoUsuario {

    public static final String JNDI_TOPICO = "java:/jms/topic/UsuariosEventos";

    @Inject
    private JMSContext contexto;

    @Resource(lookup = JNDI_TOPICO)
    private Topic topico;

    public void publicarUsuarioRegistrado(EventoUsuarioRegistrado evento) {
        contexto.createProducer().send(topico, evento);
    }
}