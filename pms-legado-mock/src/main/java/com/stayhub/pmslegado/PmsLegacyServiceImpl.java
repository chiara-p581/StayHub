package com.stayhub.pmslegado;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import java.util.List;
import java.util.logging.Logger;

/**
 * Proveedor SOAP que simula el PMS legado del hotel.
 *
 * Tiene que respetar EXACTAMENTE el contrato que espera el cliente de
 * ServicioDeCanalesExternos (com.stayhub.canalesexternos.client.pms.PmsSoapPort):
 *  - namespace:  http://pms.stayhub.com/legacy
 *  - service:    PmsLegacyService
 *  - portType:   PmsLegacyPort
 *  - operación:  sincronizarInventario(arg0 hotelId, arg1 disponibilidad[], arg2 tarifas[])
 * Por eso los parámetros NO llevan @WebParam: el cliente tampoco los nombra y
 * JAX-WS usa arg0, arg1, arg2 en los dos lados.
 *
 * Para demostrar el manejo de timeouts se puede simular un PMS lento con
 * -Dpms.demora.ms=20000 (el cliente corta a los 15 s por defecto).
 */
@WebService(
        name = "PmsLegacyPort",
        serviceName = "PmsLegacyService",
        portName = "PmsLegacyServicePort",
        targetNamespace = "http://pms.stayhub.com/legacy")
public class PmsLegacyServiceImpl {

    private static final Logger LOG = Logger.getLogger(PmsLegacyServiceImpl.class.getName());

    @WebMethod
    public RespuestaPms sincronizarInventario(Long hotelId,
                                              List<DisponibilidadPms> disponibilidad,
                                              List<TarifaPms> tarifas) {
        simularDemora();

        int cantDisp = disponibilidad == null ? 0 : disponibilidad.size();
        int cantTar = tarifas == null ? 0 : tarifas.size();
        LOG.info(() -> "[PMS] Sincronización recibida para hotel " + hotelId
                + ": " + cantDisp + " registros de disponibilidad, " + cantTar + " tarifas");
        if (disponibilidad != null) {
            disponibilidad.forEach(d -> LOG.info(() -> "[PMS]   disp " + d.tipoHabitacion
                    + " " + d.desde + ".." + d.hasta + " -> " + d.unidades + " unidades"));
        }
        if (tarifas != null) {
            tarifas.forEach(t -> LOG.info(() -> "[PMS]   tarifa " + t.tipoHabitacion
                    + " " + t.desde + ".." + t.hasta + " -> " + t.importe + " " + t.moneda));
        }

        RespuestaPms respuesta = new RespuestaPms();
        if (hotelId == null) {
            respuesta.exitoso = false;
            respuesta.mensaje = "hotelId es obligatorio";
        } else {
            respuesta.exitoso = true;
            respuesta.mensaje = "PMS actualizado: " + cantDisp + " disponibilidades y " + cantTar + " tarifas";
        }
        return respuesta;
    }

    private static void simularDemora() {
        long demora = Long.getLong("pms.demora.ms", 0L);
        if (demora <= 0) return;
        LOG.warning(() -> "[PMS] Simulando PMS lento: esperando " + demora + " ms");
        try {
            Thread.sleep(demora);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
