package com.stayhub.reservas.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad central de ServicioDeReservas.
 *
 * Representa tanto una reserva creada directamente en StayHub (canal =
 * "DIRECTA") como una reserva que llegó empujada desde un canal externo vía
 * ServicioDeCanalesExternos (canal = "BOOKING", "EXPEDIA", "AIRBNB",
 * "DESPEGAR", "OTRO" -> mismos valores que el enum Canal de canalesexternos).
 *
 * La combinación (canal, referenciaExterna) es única y se usa para evitar
 * duplicados cuando una OTA reenvía el mismo evento más de una vez. Para
 * reservas directas, referenciaExterna queda en null y canal = "DIRECTA".
 */
@Entity
@Table(
    name = "reserva",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_canal_referencia_externa",
        columnNames = {"canal", "referencia_externa"}
    )
)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String canal;

    /** Id de la reserva en el sistema externo. Null si canal = "DIRECTA". */
    @Column(name = "referencia_externa", length = 60)
    private String referenciaExterna;

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

    @Embedded
    private Huesped huesped;

    @Column(name = "precio_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioTotal;

    @Column(nullable = false, length = 3)
    private String moneda;

    /** Referencia al hold activo en ServicioDeInventarioYTarifas, mientras dure. */
    @Column(name = "hold_id", length = 60)
    private String holdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Version
    private Long version;

    protected Reserva() {
        // requerido por JPA
    }

    public Reserva(String canal, String referenciaExterna, Long hotelId,
                    String tipoHabitacion, int cantidadHabitaciones,
                    LocalDate checkIn, LocalDate checkOut, Huesped huesped,
                    BigDecimal precioTotal, String moneda) {
        this.canal = canal;
        this.referenciaExterna = referenciaExterna;
        this.hotelId = hotelId;
        this.tipoHabitacion = tipoHabitacion;
        this.cantidadHabitaciones = cantidadHabitaciones;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.huesped = huesped;
        this.precioTotal = precioTotal;
        this.moneda = moneda;
        this.estado = EstadoReserva.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
    }

    // --- transiciones de estado ---

    /** Se retuvo el cupo pero todavía no se confirmó (flujo de reserva directa con hold). */
    public void iniciarHold(String holdId) {
        this.holdId = holdId;
        this.estado = EstadoReserva.PENDIENTE;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void confirmar(String holdConfirmadoId) {
        this.holdId = holdConfirmadoId;
        this.estado = EstadoReserva.CONFIRMADA;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void rechazar() {
        this.estado = EstadoReserva.RECHAZADA;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void cancelar() {
        this.estado = EstadoReserva.CANCELADA;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void actualizarDatos(LocalDate checkIn, LocalDate checkOut, String tipoHabitacion,
                                 int cantidadHabitaciones, BigDecimal precioTotal) {
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.tipoHabitacion = tipoHabitacion;
        this.cantidadHabitaciones = cantidadHabitaciones;
        this.precioTotal = precioTotal;
        this.estado = EstadoReserva.MODIFICADA;
        this.fechaActualizacion = LocalDateTime.now();
    }

    // --- getters (sin setters sueltos, para que los cambios de estado pasen
    //     siempre por los métodos de arriba y la entidad se mantenga consistente) ---

    public Long getId() { return id; }
    public String getCanal() { return canal; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public Long getHotelId() { return hotelId; }
    public String getTipoHabitacion() { return tipoHabitacion; }
    public int getCantidadHabitaciones() { return cantidadHabitaciones; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public Huesped getHuesped() { return huesped; }
    public BigDecimal getPrecioTotal() { return precioTotal; }
    public String getMoneda() { return moneda; }
    public String getHoldId() { return holdId; }
    public EstadoReserva getEstado() { return estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
