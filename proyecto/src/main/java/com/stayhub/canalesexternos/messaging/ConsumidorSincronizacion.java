package com.stayhub.canalesexternos.messaging;

import com.stayhub.seguridad.Limites;

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
            validar(solicitud);
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

    private void validar(SolicitudSincronizacion solicitud) {
        if (solicitud == null || solicitud.destino() == null || solicitud.hotelId() == null
                || solicitud.hotelId() <= 0 || solicitud.solicitudId() == null
                || solicitud.solicitudId().length() > 64
                || (solicitud.destino() == SolicitudSincronizacion.TipoDestino.OTA && solicitud.canal() == null)) {
            throw new IllegalArgumentException("Mensaje de sincronización inválido");
        }
        Limites.rango(solicitud.desde(), solicitud.hasta());
    }}
