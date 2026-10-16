package com.stayhub.inventarioytarifas.service;
import com.stayhub.inventarioytarifas.model.InventarioDiario;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventarioTarifasSeguridadTest {
    LocalDate desde=LocalDate.now().plusDays(1),hasta=desde.plusDays(2);
    InventarioDiario dia(LocalDate fecha,String moneda){return new InventarioDiario(1L,"DOBLE",fecha,5,BigDecimal.TEN,moneda);}
    @Test void faltarUnaNocheNoProduceTarifaNiDisponibilidadParcial(){
        var dias=List.of(dia(desde,"ARS"));assertTrue(InventarioTarifasMapper.aTarifas(dias,desde,hasta).isEmpty());assertTrue(InventarioTarifasMapper.aDisponibilidad(dias,desde,hasta).isEmpty());
    }
    @Test void monedasMezcladasNoSeSuman(){assertTrue(InventarioTarifasMapper.aTarifas(List.of(dia(desde,"ARS"),dia(desde.plusDays(1),"USD")),desde,hasta).isEmpty());}
    @Test void nochesCompletasSeSumanConLaMonedaCorrecta(){var tarifas=InventarioTarifasMapper.aTarifas(List.of(dia(desde,"ARS"),dia(desde.plusDays(1),"ARS")),desde,hasta);assertEquals(new BigDecimal("20"),tarifas.get(0).importe());assertEquals("ARS",tarifas.get(0).moneda());}
}
