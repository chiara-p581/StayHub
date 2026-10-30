package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.client.ota.OtaRestClient;
import com.stayhub.canalesexternos.client.pms.PmsLegacyClient;
import com.stayhub.canalesexternos.client.pms.RespuestaPms;
import com.stayhub.canalesexternos.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.dto.DisponibilidadDTO;
import com.stayhub.canalesexternos.dto.TarifaDTO;
import com.stayhub.canalesexternos.exception.CanalExternoException;
import com.stayhub.canalesexternos.exception.CodigoErrorCanal;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Stateless
public class ProcesadorSincronizacion {

    @Inject
    private Instance<ServicioDeInventarioYTarifasPort> inventarioYTarifas;

    @Inject
    private OtaRestClient otaClient;

    @Inject
    private PmsLegacyClient pmsClient;

    public void sincronizarOta(Long hotelId, Canal canal, LocalDate desde, LocalDate hasta) {
        ServicioDeInventarioYTarifasPort inventario = inventario();
        List<DisponibilidadDTO> disponibilidad = inventario.consultarDisponibilidad(hotelId, desde, hasta);
        List<TarifaDTO> tarifas = inventario.consultarTarifas(hotelId, desde, hasta);
        conReintentoAcotado(() -> {
            otaClient.publicarDisponibilidad(canal, disponibilidad);
            return null;
        }, CodigoErrorCanal.ERROR_COMUNICACION_OTA, "OTA " + canal);
        conReintentoAcotado(() -> {
            otaClient.publicarTarifas(canal, tarifas);
            return null;
        }, CodigoErrorCanal.ERROR_COMUNICACION_OTA, "OTA " + canal);
    }

    public void sincronizarPms(Long hotelId, LocalDate desde, LocalDate hasta) {
        ServicioDeInventarioYTarifasPort inventario = inventario();
        List<DisponibilidadDTO> disponibilidad = inventario.consultarDisponibilidad(hotelId, desde, hasta);
        List<TarifaDTO> tarifas = inventario.consultarTarifas(hotelId, desde, hasta);
        RespuestaPms respuesta = conReintentoAcotado(
                () -> pmsClient.sincronizar(hotelId, disponibilidad, tarifas),
                CodigoErrorCanal.ERROR_COMUNICACION_PMS, "PMS");
        if (!respuesta.exitoso) {
            throw new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_PMS,
                    respuesta.mensaje == null ? "El PMS rechazó la sincronización" : respuesta.mensaje);
        }
    }

    /**
     * Un único reintento para operaciones idempotentes. Cada intento está
     * limitado por los timeouts del cliente, por lo que una dependencia que no
     * responde produce un fallback controlado antes de cinco segundos.
     */
    private <T> T conReintentoAcotado(Supplier<T> operacion, CodigoErrorCanal codigo, String destino) {
        CanalExternoException ultimo = null;
        for (int intento = 1; intento <= 2; intento++) {
            try {
                return operacion.get();
            } catch (CanalExternoException ex) {
                if (ex.getCodigo() != codigo) throw ex;
                ultimo = ex;
            }
        }
        throw new CanalExternoException(codigo,
                "La sincronización con " + destino + " no está disponible temporalmente; el mensaje podrá reprocesarse",
                ultimo);
    }

    private ServicioDeInventarioYTarifasPort inventario() {
        if (!inventarioYTarifas.isResolvable()) {
            throw new CanalExternoException(CodigoErrorCanal.DEPENDENCIA_NO_DISPONIBLE,
                    "ServicioDeInventarioYTarifas todavía no posee una implementación disponible");
        }
        return inventarioYTarifas.get();
    }
}
