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
                new HotelDemo("StayHub Palermo", "Thames 1800", "Buenos Aires", "Argentina", "Un refugio urbano en el corazón de Palermo Soho, a pasos de restaurantes, galerías y tiendas de diseño. Sus ambientes combinan arquitectura porteña, confort contemporáneo y espacios tranquilos para descansar después de recorrer la ciudad.", Set.of("WiFi", "Desayuno", "Recepción 24 horas"), 92000),
                new HotelDemo("Costa Serena Resort", "Ruta 11 km 397", "Mar del Plata", "Argentina", "Un resort frente al Atlántico pensado para estadías relajadas durante todo el año. Ofrece acceso directo a la playa, amplias áreas de descanso, gastronomía regional y atardeceres abiertos sobre el mar.", Set.of("Piscina", "Spa", "Estacionamiento"), 118000),
                new HotelDemo("Cumbres del Sur", "Av. Bustillo 7200", "Bariloche", "Argentina", "Refugio de montaña con grandes ventanales hacia el Nahuel Huapi y la cordillera. Maderas naturales, hogares encendidos y cocina patagónica crean una experiencia cálida cerca de senderos y centros de esquí.", Set.of("Desayuno", "Traslado", "Guardaesquíes"), 135000),
                new HotelDemo("Viñas de Mendoza", "Ruta Provincial 82", "Mendoza", "Argentina", "Hotel boutique rodeado de viñedos, jardines de lavanda y vistas abiertas a los Andes. La estadía combina descanso, degustaciones, cocina de estación y recorridos por bodegas seleccionadas.", Set.of("Piscina", "Restaurante", "Tours"), 126000),
                new HotelDemo("Selva Iguazú Lodge", "Reserva Iryapú", "Puerto Iguazú", "Argentina", "Lodge integrado al paisaje de selva misionera, con pasarelas entre árboles nativos y habitaciones silenciosas. Es una base confortable para conocer las cataratas y descubrir la biodiversidad local.", Set.of("Piscina", "Traslado", "Excursiones"), 109000),
                new HotelDemo("Patagonia Austral", "Maipú 1250", "Ushuaia", "Argentina", "Una estadía boutique frente al canal Beagle, con interiores serenos y vistas a montañas nevadas. Ideal para combinar navegación, excursiones al parque nacional y tardes de spa.", Set.of("Desayuno", "Sauna", "Traslado"), 149000),
                new HotelDemo("Quebrada Solar", "Belgrano 420", "Purmamarca", "Argentina", "Posada contemporánea construida con piedra y tonos de la quebrada. Sus patios protegidos, cocina norteña y terrazas permiten disfrutar el paisaje del Cerro de los Siete Colores.", Set.of("Desayuno", "Terraza", "Excursiones"), 87000),
                new HotelDemo("Litoral Casa de Río", "Costanera 830", "Corrientes", "Argentina", "Casa hotel sobre la ribera del Paraná, con jardines frescos y espacios luminosos. Propone una estadía pausada con sabores del litoral, paseos náuticos y vistas del río.", Set.of("Piscina", "Restaurante", "Paseos náuticos"), 98000),
                new HotelDemo("Sierras de Córdoba", "Camino del Cuadrado 210", "La Cumbre", "Argentina", "Hotel serrano entre bosques y senderos, pensado para desconectar del ritmo urbano. Cuenta con rincones de lectura, cocina casera y actividades al aire libre.", Set.of("Piscina", "Senderismo", "Estacionamiento"), 94000),
                new HotelDemo("Casa Colonial Salta", "Caseros 560", "Salta", "Argentina", "Casona restaurada cerca de la plaza principal, con patios, galerías y detalles artesanales. Combina historia local con habitaciones actuales y una propuesta gastronómica de altura.", Set.of("Desayuno", "Patio", "Tours"), 102000));

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
            } else {
                hotel = hoteles.modificarHotel(hotel.id(), new HotelRequest(demo.nombre(), demo.direccion(),
                        demo.ciudad(), demo.pais(), demo.descripcion(), demo.servicios()));
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
