package com.stayhub.inventarioytarifas.service;

import com.stayhub.inventarioytarifas.dto.*;
import com.stayhub.inventarioytarifas.model.*;
import com.stayhub.inventarioytarifas.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.stayhub.seguridad.IdentidadFixture.campo;

class ServicioInventarioCasosTest {
    ServicioDeInventarioYTarifasImpl servicio; InventarioDiarioRepository inventario; HoldRepository holds; LocalDate desde=LocalDate.now().plusDays(10),hasta=desde.plusDays(1);
    @BeforeEach void preparar() throws Exception {servicio=new ServicioDeInventarioYTarifasImpl();inventario=mock(InventarioDiarioRepository.class);holds=mock(HoldRepository.class);campo(servicio,"inventarioRepositorio",inventario);campo(servicio,"holdRepositorio",holds);}
    @Test void cargaValidaPersisteExactamenteElRangoIndicado(){
        var req=new CargaInventarioRequest(1L,"DOBLE",desde,hasta,5,new BigDecimal("120000"),"ARS");when(inventario.cargarRango(1L,"DOBLE",desde,hasta,5,new BigDecimal("120000"),"ARS")).thenReturn(1);
        var r=servicio.cargarInventario(req);assertEquals(1,r.diasCargados());verify(inventario).cargarRango(1L,"DOBLE",desde,hasta,5,new BigDecimal("120000"),"ARS");
    }
    @Test void fechasInvertidasCupoNegativoYTarifaFueraDeLimiteNoPersisten(){
        assertThrows(RuntimeException.class,()->servicio.cargarInventario(new CargaInventarioRequest(1L,"DOBLE",hasta,desde,1,BigDecimal.TEN,"ARS")));
        assertThrows(RuntimeException.class,()->servicio.cargarInventario(new CargaInventarioRequest(1L,"DOBLE",desde,hasta,-1,BigDecimal.TEN,"ARS")));
        assertThrows(RuntimeException.class,()->servicio.cargarInventario(new CargaInventarioRequest(1L,"DOBLE",desde,hasta,1,new BigDecimal("1234567890123"),"ARS")));
        verifyNoInteractions(inventario);
    }
    @Test void dosPedidosPorUltimoCupoSoloPermitenElPrimero(){
        var dia=new InventarioDiario(1L,"DOBLE",desde,1,BigDecimal.TEN,"ARS");when(inventario.buscarPorHotelTipoYFechaBloqueando(1L,"DOBLE",desde)).thenReturn(Optional.of(dia));
        assertNotNull(servicio.crearHold(1L,"DOBLE",1,desde,hasta));assertEquals(0,dia.getUnidadesDisponibles());
        assertThrows(RuntimeException.class,()->servicio.crearHold(1L,"DOBLE",1,desde,hasta));assertEquals(0,dia.getUnidadesDisponibles());verify(holds,times(1)).guardar(any());
    }
}
