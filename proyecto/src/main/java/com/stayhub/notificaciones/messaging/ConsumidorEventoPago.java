package com.stayhub.notificaciones.messaging;

import com.stayhub.hoteles.contrato.ServicioDeHotelesPort;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.service.EnvioCorreoIdempotente;
import com.stayhub.notificaciones.service.PlantillaEmail;
import com.stayhub.pagos.messaging.EventoPagoAprobado;
import com.stayhub.pagos.messaging.PublicadorEventoPago;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.service.ServicioDeReservas;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
    private ServicioDeHotelesPort hoteles;

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
            Map<String, String> datosPago = new LinkedHashMap<>();
            datosPago.put("Número de pago", "#" + e.pagoId());
            datosPago.put("Importe", PlantillaEmail.monto(e.monto(), e.moneda()));
            datosPago.put("Estado", "Aprobado");
            datosPago.put("Comprobante", e.referenciaPasarela());

            envio.enviar("pago:" + e.pagoId() + ":" + r.email().toLowerCase(Locale.ROOT), new SolicitudNotificacionDTO(
                    r.email(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL,
                    "Confirmación de pago — StayHub",
                    PlantillaEmail.armar(
                            "Recibimos tu pago",
                            null,
                            "Tu pago fue aprobado correctamente. Este importe corresponde al total de tu compra "
                                    + "y puede incluir varias reservas: te mandamos la confirmación de cada una por separado.",
                            datosPago,
                            null, null,
                            "Guardá este correo como comprobante.")));

            // Mail 2: confirmación de cada reserva
            Map<String, String> datosReserva = new LinkedHashMap<>();
            datosReserva.put("Reserva", "#" + e.reservaId());
            datosReserva.put("Hotel", nombreHotel(r.hotelId()));
            datosReserva.put("Habitación", r.tipoHabitacion());
            datosReserva.put("Check-in", PlantillaEmail.fecha(r.checkIn()));
            datosReserva.put("Check-out", PlantillaEmail.fecha(r.checkOut()));
            if (r.checkIn() != null && r.checkOut() != null) {
                long noches = ChronoUnit.DAYS.between(r.checkIn(), r.checkOut());
                datosReserva.put("Estadía", noches + (noches == 1 ? " noche" : " noches"));
            }

            envio.enviar("reserva-pagada:" + e.pagoId() + ":" + e.reservaId(), new SolicitudNotificacionDTO(
                    r.email(), TipoEvento.CONFIRMACION_RESERVA, CanalNotificacion.EMAIL,
                    "Confirmación de reserva #" + e.reservaId() + " — StayHub",
                    PlantillaEmail.armar(
                            "¡Tu reserva está confirmada!",
                            null,
                            "Ya está todo listo para tu estadía. Estos son los datos de tu reserva:",
                            datosReserva,
                            null, null,
                            "Presentá este correo o el número de reserva al hacer el check-in.")));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo procesar el evento de pago", ex);
        }
    }

    /** Busca el nombre del hotel; si no puede, muestra el número para no frenar el envío. */
    private String nombreHotel(Long hotelId) {
        try {
            if (hoteles != null && hotelId != null) return hoteles.consultarHotel(hotelId).nombre();
        } catch (RuntimeException ignorada) {
            // el mail sale igual, con el número del hotel
        }
        return "Hotel #" + hotelId;
    }
}