package com.stayhub.canalesexternos.messaging;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(
                propertyName = "destinationLookup",
                propertyValue = PublicadorSincronizacion.JNDI_COLA),
        @ActivationConfigProperty(
                propertyName = "destinationType",
                propertyValue = "jakarta.jms.Queue")
})
public class ConsumidorSincronizacion implements MessageListener {

    @Inject
    private ProcesadorSincronizacion procesador;

    @Override
    public void onMessage(Message mensaje) {
        try {
            SolicitudSincronizacion solicitud = mensaje.getBody(SolicitudSincronizacion.class);
            switch (solicitud.destino()) {
                case OTA -> procesador.sincronizarOta(solicitud.hotelId(), solicitud.canal(),
                        solicitud.desde(), solicitud.hasta());
                case PMS -> procesador.sincronizarPms(solicitud.hotelId(), solicitud.desde(), solicitud.hasta());
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo procesar el mensaje de sincronización", ex);
        }
    }
}
