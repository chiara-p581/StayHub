package com.stayhub.reservas.api;

import com.stayhub.reservas.carrito.CarritoDeReserva;
import com.stayhub.reservas.dto.CarritoResumenDTO;
import com.stayhub.reservas.dto.ReservaResponse;
import com.stayhub.reservas.dto.SeleccionFechasRequest;
import com.stayhub.reservas.dto.SeleccionHotelYHabitacionRequest;
import com.stayhub.reservas.dto.SeleccionHuespedYPrecioRequest;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.Serializable;

/**
 * Capa de presentación del carrito de reserva.
 *
 * @SessionScoped (CDI, atado a la sesión HTTP del cliente): mientras el
 * cliente mantenga la cookie de sesión (JSESSIONID), todas sus llamadas
 * caen sobre la MISMA instancia de este recurso y, por lo tanto, sobre el
 * mismo CarritoDeReserva inyectado — eso es lo que permite ir armando la
 * reserva en varios pasos independientes sin reenviar todo en cada
 * llamada. Los beans @SessionScoped deben ser Serializable (lo pide la
 * especificación de CDI).
 *
 * Base: /StayHub/api/carrito
 */
@Path("/carrito")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SessionScoped
public class CarritoResource implements Serializable {

    @Inject
    private CarritoDeReserva carrito;

    @POST
    @Path("/hotel")
    public CarritoResumenDTO seleccionarHotel(SeleccionHotelYHabitacionRequest s) {
        carrito.seleccionarHotelYHabitacion(s.hotelId(), s.tipoHabitacion(), s.cantidadHabitaciones());
        return carrito.resumen();
    }

    @POST
    @Path("/fechas")
    public CarritoResumenDTO seleccionarFechas(SeleccionFechasRequest s) {
        carrito.seleccionarFechas(s.checkIn(), s.checkOut());
        return carrito.resumen();
    }

    @POST
    @Path("/huesped")
    public CarritoResumenDTO seleccionarHuesped(SeleccionHuespedYPrecioRequest s) {
        carrito.seleccionarHuespedYPrecio(s.huespedNombre(), s.huespedApellido(), s.huespedEmail(),
                s.huespedTelefono(), s.precioTotal(), s.moneda());
        return carrito.resumen();
    }

    @GET
    public CarritoResumenDTO resumen() {
        return carrito.resumen();
    }

    @POST
    @Path("/confirmar")
    public ReservaResponse confirmar() {
        return carrito.confirmar();
    }

    @DELETE
    public void vaciar() {
        carrito.vaciar();
    }
}
