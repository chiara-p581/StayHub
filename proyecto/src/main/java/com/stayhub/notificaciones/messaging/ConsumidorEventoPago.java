package com.stayhub.notificaciones.messaging;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import com.stayhub.pagos.messaging.PublicadorEventoPago;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.service.ServicioDeReservas;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.util.Locale;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = PublicadorEventoPago.JNDI_TOPICO),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "stayhub-email-pagos"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "email-pagos"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class ConsumidorEventoPago implements MessageListener {

    @Inject
    private ServicioDeReservas reservas;

    @Inject
    private EnvioCorreoIdempotente envio;

    @Override
    public void onMessage(Message mensaje) {
        try {
            EventoPagoAprobado e = mensaje.getBody(EventoPagoAprobado.class);
            EventoPagoAprobado.ReservaPagada r = e.reservaPagada();
            if (r == null) {
                ReservaResponse actual = reservas.consultarReserva(e.reservaId());
                r = new EventoPagoAprobado.ReservaPagada(actual.huespedEmail(), actual.hotelId(),
                        actual.tipoHabitacion(), actual.checkIn(), actual.checkOut());
            }
            if (r.email() == null || r.email().isBlank())
                throw new IllegalStateException("La reserva no tiene email de destinatario");

            // Mail 1: comprobante de pago (uno solo por pago, aunque el lote tenga varias reservas)
            envio.enviar("pago:" + e.pagoId() + ":" + r.email().toLowerCase(Locale.ROOT), new SolicitudNotificacionDTO(
                    r.email(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL,
                    "Confirmación de pago — StayHub",
                    "Tu pago #" + e.pagoId() + " por " + e.monto() + " " + e.moneda()
                            + " fue aprobado. Referencia: " + e.referenciaPasarela()
                            + ". El importe corresponde al pago completo y puede incluir varias reservas. "
                            + "Recibirás sus confirmaciones por separado."));

            // Mail 2: confirmación de cada reserva
            envio.enviar("reserva-pagada:" + e.pagoId() + ":" + e.reservaId(), new SolicitudNotificacionDTO(
                    r.email(), TipoEvento.CONFIRMACION_RESERVA, CanalNotificacion.EMAIL,
                    "Confirmación de reserva #" + e.reservaId() + " — StayHub",
                    "Después de aprobarse tu pago, se confirmó tu reserva #" + e.reservaId()
                            + " en el hotel #" + r.hotelId() + ", habitación " + r.tipoHabitacion()
                            + ", del " + r.checkIn() + " al " + r.checkOut() + "."));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo procesar el evento de pago", ex);
        }
    }
}