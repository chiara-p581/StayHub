package com.stayhub.demo;

import com.stayhub.hoteles.dto.HabitacionRequest;
import com.stayhub.hoteles.dto.HotelRequest;
import com.stayhub.hoteles.dto.HotelResponse;
import com.stayhub.hoteles.dto.TipoHabitacionRequest;
import com.stayhub.hoteles.dto.TipoHabitacionResponse;
import com.stayhub.hoteles.service.ServicioDeHoteles;
import com.stayhub.inventarioytarifas.contrato.ServicioDeInventarioYTarifas;
import com.stayhub.inventarioytarifas.dto.CargaInventarioRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Carga idempotente de un catálogo demostrativo usando los servicios reales. */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class DemoDataResource {
    @Inject ServicioDeHoteles hoteles;
    @Inject ServicioDeInventarioYTarifas inventario;

    private record HotelDemo(String nombre, String direccion, String ciudad, String pais,
                             String descripcion, Set<String> servicios, int basePrecio) {}

    @POST
    @Path("/cargar")
    public Map<String, Object> cargar() {
        List<HotelDemo> catalogo = List.of(
                new HotelDemo("StayHub Palermo", "Thames 1800", "Buenos Aires", "Argentina", "Hotel urbano en Palermo Soho", Set.of("WiFi", "Desayuno", "Recepción 24 horas"), 92000),
                new HotelDemo("Costa Serena Resort", "Ruta 11 km 397", "Mar del Plata", "Argentina", "Resort frente al mar", Set.of("Piscina", "Spa", "Estacionamiento"), 118000),
                new HotelDemo("Cumbres del Sur", "Av. Bustillo 7200", "Bariloche", "Argentina", "Refugio con vista al Nahuel Huapi", Set.of("Desayuno", "Traslado", "Guardaesquíes"), 135000),
                new HotelDemo("Viñas de Mendoza", "Ruta Provincial 82", "Mendoza", "Argentina", "Hotel entre viñedos y montaña", Set.of("Piscina", "Restaurante", "Tours"), 126000),
                new HotelDemo("Selva Iguazú Lodge", "Reserva Iryapú", "Puerto Iguazú", "Argentina", "Lodge integrado con la selva", Set.of("Piscina", "Traslado", "Excursiones"), 109000),
                new HotelDemo("Patagonia Austral", "Maipú 1250", "Ushuaia", "Argentina", "Estadía boutique en el fin del mundo", Set.of("Desayuno", "Sauna", "Traslado"), 149000));

        int creados = 0;
        LocalDate desde = LocalDate.now();
        LocalDate hasta = desde.plusMonths(6);
        for (HotelDemo demo : catalogo) {
            HotelResponse hotel = hoteles.listarHoteles(true).stream()
                    .filter(h -> h.nombre().equalsIgnoreCase(demo.nombre())).findFirst().orElse(null);
            if (hotel == null) {
                hotel = hoteles.crearHotel(new HotelRequest(demo.nombre(), demo.direccion(), demo.ciudad(),
                        demo.pais(), demo.descripcion(), demo.servicios()));
                creados++;
            }
            cargarTipo(hotel.id(), "STANDARD", "Habitación standard", 2, demo.basePrecio(), desde, hasta);
            cargarTipo(hotel.id(), "SUITE", "Suite superior", 4, (int) (demo.basePrecio() * 1.65), desde, hasta);
        }
        return Map.of("hotelesCreados", creados, "hotelesDisponibles", catalogo.size(),
                "inventarioDesde", desde, "inventarioHasta", hasta,
                "mensaje", "Catálogo demo e inventario cargados mediante los servicios reales");
    }

    private void cargarTipo(Long hotelId, String codigo, String nombre, int capacidad, int precio,
                            LocalDate desde, LocalDate hasta) {
        TipoHabitacionResponse tipo = hoteles.listarTipos(hotelId, true).stream()
                .filter(t -> t.codigo().equalsIgnoreCase(codigo)).findFirst().orElse(null);
        if (tipo == null) {
            tipo = hoteles.crearTipo(hotelId, new TipoHabitacionRequest(codigo, nombre,
                    "Opción demostrativa con disponibilidad real", capacidad, Set.of("Baño privado", "WiFi")));
        }
        var habitaciones = hoteles.listarHabitaciones(hotelId, true);
        for (int i = 1; i <= 5; i++) {
            String numero = (codigo.equals("STANDARD") ? "3" : "4") + String.format("%02d", i);
            if (habitaciones.stream().noneMatch(h -> h.numero().equals(numero))) {
                hoteles.crearHabitacion(hotelId, new HabitacionRequest(tipo.id(), numero,
                        codigo.equals("STANDARD") ? 3 : 4, Set.of("No fumadores")));
            }
        }
        inventario.cargarInventario(new CargaInventarioRequest(hotelId, codigo, desde, hasta,
                5, BigDecimal.valueOf(precio), "ARS"));
    }
}
