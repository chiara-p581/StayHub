package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.client.ota.OtaRestClient;
import com.stayhub.canalesexternos.client.pms.*;
import com.stayhub.canalesexternos.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.exception.CanalExternoException;
import com.stayhub.canalesexternos.exception.CodigoErrorCanal;
import jakarta.enterprise.inject.Instance;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.stayhub.seguridad.IdentidadFixture.campo;

class ProcesadorSincronizacionTest {
    ProcesadorSincronizacion procesador; OtaRestClient ota; PmsLegacyClient pms; ServicioDeInventarioYTarifasPort inventario; LocalDate desde=LocalDate.now().plusDays(1),hasta=desde.plusDays(1);
    @BeforeEach void preparar() throws Exception {procesador=new ProcesadorSincronizacion();ota=mock(OtaRestClient.class);pms=mock(PmsLegacyClient.class);inventario=mock(ServicioDeInventarioYTarifasPort.class);Instance<ServicioDeInventarioYTarifasPort> instancia=mock(Instance.class);when(instancia.isResolvable()).thenReturn(true);when(instancia.get()).thenReturn(inventario);when(inventario.consultarDisponibilidad(1L,desde,hasta)).thenReturn(List.of());when(inventario.consultarTarifas(1L,desde,hasta)).thenReturn(List.of());campo(procesador,"inventarioYTarifas",instancia);campo(procesador,"otaClient",ota);campo(procesador,"pmsClient",pms);}
    @Test void timeoutOErrorRestSePropagaYNoSeMarcaComoExito(){doThrow(new IllegalStateException("timeout controlado")).when(ota).publicarDisponibilidad(any(),any());assertThrows(RuntimeException.class,()->procesador.sincronizarOta(1L,Canal.BOOKING,desde,hasta));verify(ota,never()).publicarTarifas(any(),any());}
    @Test void faultORechazoSoapSePropagaParaReintento(){var respuesta=new RespuestaPms();respuesta.exitoso=false;respuesta.mensaje="Fault controlado";when(pms.sincronizar(1L,List.of(),List.of())).thenReturn(respuesta);assertThrows(RuntimeException.class,()->procesador.sincronizarPms(1L,desde,hasta));}

    @Test void otaSeRecuperaConUnUnicoReintento() {
        doThrow(new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_OTA, "timeout"))
                .doNothing().when(ota).publicarDisponibilidad(Canal.BOOKING, List.of());

        procesador.sincronizarOta(1L, Canal.BOOKING, desde, hasta);

        verify(ota, times(2)).publicarDisponibilidad(Canal.BOOKING, List.of());
        verify(ota).publicarTarifas(Canal.BOOKING, List.of());
    }

    @Test void otaAgotaDosIntentosYComunicaElFallback() {
        doThrow(new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_OTA, "timeout"))
                .when(ota).publicarDisponibilidad(Canal.BOOKING, List.of());

        var error = assertThrows(CanalExternoException.class,
                () -> procesador.sincronizarOta(1L, Canal.BOOKING, desde, hasta));

        assertEquals(CodigoErrorCanal.ERROR_COMUNICACION_OTA, error.getCodigo());
        assertTrue(error.getMessage().contains("reprocesarse"));
        verify(ota, times(2)).publicarDisponibilidad(Canal.BOOKING, List.of());
        verify(ota, never()).publicarTarifas(any(), any());
    }

    @Test void pmsSeRecuperaConUnUnicoReintento() {
        var respuesta = new RespuestaPms();
        respuesta.exitoso = true;
        when(pms.sincronizar(1L, List.of(), List.of()))
                .thenThrow(new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_PMS, "timeout"))
                .thenReturn(respuesta);

        procesador.sincronizarPms(1L, desde, hasta);

        verify(pms, times(2)).sincronizar(1L, List.of(), List.of());
    }
}
