package com.stayhub.pagos.messaging;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.Topic;

@ApplicationScoped
@JMSDestinationDefinition(
        name = PublicadorEventoPago.JNDI_TOPICO,
        interfaceName = "jakarta.jms.Topic",
        destinationName = "PagosEventos")
public class PublicadorEventoPago {

    public static final String JNDI_TOPICO = "java:/jms/topic/PagosEventos";

    @Inject
    private JMSContext contexto;

    @Resource(lookup = JNDI_TOPICO)
    private Topic topico;

    public void publicarPagoAprobado(EventoPagoAprobado evento) {
        contexto.createProducer().send(topico, evento);
    }
}