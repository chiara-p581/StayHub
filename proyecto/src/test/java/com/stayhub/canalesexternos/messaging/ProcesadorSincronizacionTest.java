package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.client.ota.OtaRestClient;
import com.stayhub.canalesexternos.client.pms.*;
import com.stayhub.canalesexternos.contrato.interno.ServicioDeInventarioYTarifasPort;
import com.stayhub.canalesexternos.dto.Canal;
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
}
