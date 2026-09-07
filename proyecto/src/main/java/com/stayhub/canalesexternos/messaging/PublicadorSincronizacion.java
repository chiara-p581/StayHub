package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.dto.Canal;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.Queue;

import java.time.LocalDate;
import java.util.UUID;

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

    public String publicarOta(Long hotelId, Canal canal, LocalDate desde, LocalDate hasta) {
        String solicitudId = UUID.randomUUID().toString();
        publicar(SolicitudSincronizacion.paraOta(solicitudId, hotelId, canal, desde, hasta));
        return solicitudId;
    }

    public String publicarPms(Long hotelId, LocalDate desde, LocalDate hasta) {
        String solicitudId = UUID.randomUUID().toString();
        publicar(SolicitudSincronizacion.paraPms(solicitudId, hotelId, desde, hasta));
        return solicitudId;
    }

    private void publicar(SolicitudSincronizacion solicitud) {
        contexto.createProducer().setJMSCorrelationID(solicitud.solicitudId()).send(cola, solicitud);
    }
}
