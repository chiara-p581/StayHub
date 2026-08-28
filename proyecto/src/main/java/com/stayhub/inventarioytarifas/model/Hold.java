package com.stayhub.inventarioytarifas.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Retención de cupo pedida por ServicioDeReservas (GestionDeDisponibilidadPort)
 * mientras una reserva está pendiente de confirmación. Es la entidad que hace
 * "stateful" a ServicioDeInventarioYTarifas: el cupo se descuenta de
 * InventarioDiario en crearHold y el hold queda persistido hasta que llega
 * confirmarHold o liberarHold, en una llamada posterior e independiente.
 *
 * El id es una clave natural (String, UUID) y no un id autogenerado: lo
 * devuelve crearHold como "holdId" y ServicioDeReservas lo guarda para
 * volver a referenciarlo en confirmarHold/liberarHold.
 */
@Entity
@Table(name = "hold_disponibilidad")
public class Hold {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "tipo_habitacion", nullable = false, length = 30)
    private String tipoHabitacion;

    @Column(name = "cantidad_habitaciones", nullable = false)
    private int cantidadHabitaciones;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoHold estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    protected Hold() {
        // requerido por JPA
    }

    public Hold(String id, Long hotelId, String tipoHabitacion, int cantidadHabitaciones,
                LocalDate checkIn, LocalDate checkOut) {
        this.id = id;
        this.hotelId = hotelId;
        this.tipoHabitacion = tipoHabitacion;
        this.cantidadHabitaciones = cantidadHabitaciones;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.estado = EstadoHold.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
    }

    public void confirmar() {
        this.estado = EstadoHold.CONFIRMADO;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void liberar() {
        this.estado = EstadoHold.LIBERADO;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String getId() { return id; }
    public Long getHotelId() { return hotelId; }
    public String getTipoHabitacion() { return tipoHabitacion; }
    public int getCantidadHabitaciones() { return cantidadHabitaciones; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public EstadoHold getEstado() { return estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
