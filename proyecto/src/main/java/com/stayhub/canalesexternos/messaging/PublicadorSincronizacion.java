package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.dto.Canal;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.Queue;

import java.time.LocalDate;

@ApplicationScoped
@JMSDestinationDefinition(
        name = PublicadorSincronizacion.JNDI_COLA,
        interfaceName = "jakarta.jms.Queue",
        destinationName = "CanalesExternosSincronizaciones")
public class PublicadorSincronizacion {

    public static final String JNDI_COLA = "java:/jms/queue/CanalesExternosSincronizaciones";

    @Inject
    private JMSContext contexto;

    @Resource(lookup = JNDI_COLA)
    private Queue cola;

    public void publicarOta(Long hotelId, Canal canal, LocalDate desde, LocalDate hasta) {
        publicar(SolicitudSincronizacion.paraOta(hotelId, canal, desde, hasta));
    }

    public void publicarPms(Long hotelId, LocalDate desde, LocalDate hasta) {
        publicar(SolicitudSincronizacion.paraPms(hotelId, desde, hasta));
    }

    private void publicar(SolicitudSincronizacion solicitud) {
        contexto.createProducer().send(cola, solicitud);
    }
}
