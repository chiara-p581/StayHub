package com.stayhub.servicioDeOverbooking.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "conflicto_overbooking")
public class ConflictoOverbooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id_conflictiva", nullable = false)
    private Long reservaIdConflictiva;

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

    @Column(name = "canal_origen", length = 30)
    private String canalOrigen;

    @Column(name = "referencia_externa", length = 60)
    private String referenciaExterna;

    @Column(name = "huesped_email", length = 120)
    private String huespedEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "estrategia_aplicada", length = 30)
    private EstrategiaResolucion estrategiaAplicada;

    @Column(nullable = false)
    private boolean resuelto;

    /** Id del hold retenido en InventarioYTarifas para la habitación alternativa (null si la estrategia fue COMPENSACION). */
    @Column(name = "hold_alternativo_id", length = 60)
    private String holdAlternativoId;

    @Column(name = "tipo_habitacion_alternativa", length = 30)
    private String tipoHabitacionAlternativa;

    @Column(length = 255)
    private String mensaje;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    protected ConflictoOverbooking() {
        // requerido por JPA
    }

    public ConflictoOverbooking(Long reservaIdConflictiva, Long hotelId, String tipoHabitacion, int cantidadHabitaciones,
                                 LocalDate checkIn, LocalDate checkOut, String canalOrigen, String referenciaExterna,
                                 String huespedEmail) {
        this.reservaIdConflictiva = reservaIdConflictiva;
        this.hotelId = hotelId;
        this.tipoHabitacion = tipoHabitacion;
        this.cantidadHabitaciones = cantidadHabitaciones;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.canalOrigen = canalOrigen;
        this.referenciaExterna = referenciaExterna;
        this.huespedEmail = huespedEmail;
        this.resuelto = false;
        this.fechaCreacion = LocalDateTime.now();
    }

    public void resolverPorReubicacion(String tipoHabitacionAlternativa, String holdAlternativoId, String mensaje) {
        this.estrategiaAplicada = EstrategiaResolucion.REUBICACION;
        this.tipoHabitacionAlternativa = tipoHabitacionAlternativa;
        this.holdAlternativoId = holdAlternativoId;
        this.mensaje = mensaje;
        this.resuelto = true;
    }

    public void resolverPorCompensacion(String mensaje) {
        this.estrategiaAplicada = EstrategiaResolucion.COMPENSACION;
        this.mensaje = mensaje;
        this.resuelto = true;
    }

    public Long getId() { return id; }
    public Long getReservaIdConflictiva() { return reservaIdConflictiva; }
    public Long getHotelId() { return hotelId; }
    public String getTipoHabitacion() { return tipoHabitacion; }
    public int getCantidadHabitaciones() { return cantidadHabitaciones; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public String getCanalOrigen() { return canalOrigen; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public String getHuespedEmail() { return huespedEmail; }
    public EstrategiaResolucion getEstrategiaAplicada() { return estrategiaAplicada; }
    public boolean isResuelto() { return resuelto; }
    public String getHoldAlternativoId() { return holdAlternativoId; }
    public String getTipoHabitacionAlternativa() { return tipoHabitacionAlternativa; }
    public String getMensaje() { return mensaje; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}