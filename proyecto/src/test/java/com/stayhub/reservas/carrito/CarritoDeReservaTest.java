package com.stayhub.reservas.carrito;

import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.TarifaDTO;
import com.stayhub.reservas.service.ServicioDeReservas;
import com.stayhub.usuarios.contrato.ServicioDeUsuarios;
import com.stayhub.usuarios.dto.UsuarioResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.stayhub.seguridad.IdentidadFixture.*;

class CarritoDeReservaTest {
    CarritoDeReserva carrito; ServicioDeInventarioYTarifas inventario; LocalDate desde=LocalDate.now().plusDays(5),hasta=desde.plusDays(2);
    @BeforeEach void preparar() throws Exception {carrito=new CarritoDeReserva();inventario=mock(ServicioDeInventarioYTarifas.class);ServicioDeUsuarios usuarios=mock(ServicioDeUsuarios.class);campo(carrito,"identidad",usuario(1L,"HUESPED"));campo(carrito,"usuarios",usuarios);campo(carrito,"inventarioYTarifas",inventario);campo(carrito,"servicioDeReservas",mock(ServicioDeReservas.class));when(usuarios.buscarPorId(1L)).thenReturn(new UsuarioResponse(1L,"qa@test.com","QA","Huesped","HUESPED"));when(inventario.consultarTarifas(1L,desde,hasta)).thenReturn(List.of(new TarifaDTO(1L,"DOBLE",desde,hasta,new BigDecimal("200"),"ARS")));}
    @Test void muestraSoloElContenidoDeSuInstanciaYVaciarRecalculaTotal(){
        carrito.seleccionarHotelYHabitacion(1L,"DOBLE",2);carrito.seleccionarFechas(desde,hasta);carrito.seleccionarHuespedYPrecio("x","y","otro@test.com","123",BigDecimal.ONE,"USD");
        assertTrue(carrito.resumen().listoParaConfirmar());assertEquals(new BigDecimal("400"),carrito.resumen().precioTotal());assertEquals("qa@test.com",carrito.resumen().huespedEmail());
        carrito.vaciar();assertFalse(carrito.resumen().listoParaConfirmar());assertNull(carrito.resumen().precioTotal());
    }
    @Test void itemInvalidoNoSeIncorpora(){
        assertThrows(RuntimeException.class,()->carrito.seleccionarHotelYHabitacion(-1L,"DOBLE",1));assertThrows(RuntimeException.class,()->carrito.seleccionarFechas(hasta,desde));assertFalse(carrito.resumen().listoParaConfirmar());
    }
}
