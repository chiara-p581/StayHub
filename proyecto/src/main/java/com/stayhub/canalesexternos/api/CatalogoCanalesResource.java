package com.stayhub.canalesexternos.api;

import com.stayhub.canalesexternos.dto.OfertaCatalogoDTO;
import com.stayhub.hoteles.service.ServicioDeHoteles;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock API navegable: combina el catálogo real de StayHub con publicaciones
 * determinísticas de OTAs. No llama a Booking/Airbnb reales ni usa sus marcas
 * como integración productiva; sirve para probar filtros y flujos multicanal.
 */
@Path("/catalogo-canales/ofertas")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogoCanalesResource {

    private static final String[] OTAS = {"BOOKING", "AIRBNB", "EXPEDIA", "DESPEGAR"};

    @Inject
    private ServicioDeHoteles hoteles;

    @GET
    public List<OfertaCatalogoDTO> listar() {
        List<OfertaCatalogoDTO> ofertas = new ArrayList<>();
        hoteles.listarHoteles(false).forEach(hotel -> {
            long id = hotel.id();
            BigDecimal base = BigDecimal.valueOf(78000 + (id % 9) * 8500);
            ofertas.add(new OfertaCatalogoDTO(id, "STAYHUB", base, "ARS",
                    4.2 + (id % 7) / 10.0, 85 + (int) (id % 12) * 31));
            String ota = OTAS[(int) (id % OTAS.length)];
            ofertas.add(new OfertaCatalogoDTO(id, ota, base.multiply(BigDecimal.valueOf(1.08)), "ARS",
                    4.1 + (id % 8) / 10.0, 140 + (int) (id % 10) * 47));
        });
        return ofertas;
    }
}
