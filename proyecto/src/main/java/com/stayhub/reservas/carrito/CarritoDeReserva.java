package com.stayhub.reservas.carrito;

import com.stayhub.reservas.dto.CarritoResumenDTO;
import com.stayhub.reservas.dto.ReservaRequest;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.service.ServicioDeReservas;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Remove;
import jakarta.ejb.Stateful;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Carrito de armado de una reserva: mantiene en la MEMORIA de esta instancia
 * las elecciones que un huésped va haciendo (hotel, fechas, huésped, precio)
 * antes de confirmar, mientras dura su sesión de navegación en StayHub.
 *
 * A diferencia del resto del sistema —todo @Stateless, con cualquier estado
 * persistido en base de datos (ver README de InventarioYTarifas)— este es
 * el único componente conceptualmente stateful de verdad: el contenedor
 * crea UNA instancia por sesión de usuario (queda atada a CarritoResource,
 * que es @SessionScoped) y esa instancia recuerda lo elegido entre llamadas
 * HTTP independientes, sin que el cliente tenga que reenviar todo de nuevo
 * en cada paso.
 *
 * @PostConstruct / @PreDestroy son evidencia de que el CONTENEDOR gestiona
 * el ciclo de vida de esta conversación, no el código de negocio: se
 * ejecutan solos, al crearse y al destruirse la instancia (fin de sesión
 * HTTP, timeout, o cuando confirmar() la remueve explícitamente con
 * @Remove).
 */
@Stateful
public class CarritoDeReserva {

    @Inject
    private ServicioDeReservas servicioDeReservas;

    private Long hotelId;
    private String tipoHabitacion;
    private int cantidadHabitaciones;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String huespedNombre;
    private String huespedApellido;
    private String huespedEmail;
    private String huespedTelefono;
    private BigDecimal precioTotal;
    private String moneda;

    @PostConstruct
    private void iniciar() {
        System.out.println("[CarritoDeReserva] nueva conversación iniciada (instancia " + this.hashCode() + ")");
    }

    @PreDestroy
    private void finalizar() {
        System.out.println("[CarritoDeReserva] conversación finalizada (instancia " + this.hashCode() + ")");
    }

    public void seleccionarHotelYHabitacion(Long hotelId, String tipoHabitacion, int cantidadHabitaciones) {
        this.hotelId = hotelId;
        this.tipoHabitacion = tipoHabitacion;
        this.cantidadHabitaciones = cantidadHabitaciones;
    }

    public void seleccionarFechas(LocalDate checkIn, LocalDate checkOut) {
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public void seleccionarHuespedYPrecio(String huespedNombre, String huespedApellido, String huespedEmail,
                                           String huespedTelefono, BigDecimal precioTotal, String moneda) {
        this.huespedNombre = huespedNombre;
        this.huespedApellido = huespedApellido;
        this.huespedEmail = huespedEmail;
        this.huespedTelefono = huespedTelefono;
        this.precioTotal = precioTotal;
        this.moneda = moneda;
    }

    public CarritoResumenDTO resumen() {
        boolean listo = hotelId != null && tipoHabitacion != null && cantidadHabitaciones > 0
                && checkIn != null && checkOut != null && huespedNombre != null && huespedApellido != null
                && huespedEmail != null && huespedTelefono != null && precioTotal != null && moneda != null;
        return new CarritoResumenDTO(hotelId, tipoHabitacion, cantidadHabitaciones, checkIn, checkOut,
                huespedNombre, huespedApellido, huespedEmail, huespedTelefono, precioTotal, moneda, listo);
    }

    /**
     * @Remove: al confirmar, el contenedor destruye esta instancia apenas
     * termina el método (dispara @PreDestroy) — la conversación de armado
     * terminó. De acá en más la reserva vive como una entidad normal,
     * administrada por ServicioDeReservas (@Stateless) como cualquier otra:
     * el carrito le entrega el trabajo al componente stateless y desaparece.
     */
    @Remove
    public ReservaResponse confirmar() {
        ReservaRequest solicitud = new ReservaRequest(hotelId, tipoHabitacion, cantidadHabitaciones,
                checkIn, checkOut, huespedNombre, huespedApellido, huespedEmail, huespedTelefono,
                precioTotal, moneda);
        return servicioDeReservas.crearReserva(solicitud);
    }

    public void vaciar() {
        hotelId = null;
        tipoHabitacion = null;
        cantidadHabitaciones = 0;
        checkIn = null;
        checkOut = null;
        huespedNombre = null;
        huespedApellido = null;
        huespedEmail = null;
        huespedTelefono = null;
        precioTotal = null;
        moneda = null;
    }
}
