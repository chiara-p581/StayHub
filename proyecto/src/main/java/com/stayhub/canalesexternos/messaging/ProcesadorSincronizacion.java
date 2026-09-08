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
        otaClient.publicarDisponibilidad(canal, disponibilidad);
        otaClient.publicarTarifas(canal, tarifas);
    }

    public void sincronizarPms(Long hotelId, LocalDate desde, LocalDate hasta) {
        ServicioDeInventarioYTarifasPort inventario = inventario();
        List<DisponibilidadDTO> disponibilidad = inventario.consultarDisponibilidad(hotelId, desde, hasta);
        List<TarifaDTO> tarifas = inventario.consultarTarifas(hotelId, desde, hasta);
        RespuestaPms respuesta = pmsClient.sincronizar(hotelId, disponibilidad, tarifas);
        if (!respuesta.exitoso) {
            throw new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_PMS,
                    respuesta.mensaje == null ? "El PMS rechazó la sincronización" : respuesta.mensaje);
        }
    }

    private ServicioDeInventarioYTarifasPort inventario() {
        if (!inventarioYTarifas.isResolvable()) {
            throw new CanalExternoException(CodigoErrorCanal.DEPENDENCIA_NO_DISPONIBLE,
                    "ServicioDeInventarioYTarifas todavía no posee una implementación disponible");
        }
        return inventarioYTarifas.get();
    }
}
