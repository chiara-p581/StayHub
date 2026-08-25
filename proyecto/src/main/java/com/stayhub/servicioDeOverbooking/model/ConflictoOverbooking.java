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

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "canal_origen", length = 30)
    private String canalOrigen;

    @Column(name = "referencia_externa", length = 60)
    private String referenciaExterna;

    @Enumerated(EnumType.STRING)
    @Column(name = "estrategia_aplicada", length = 30)
    private EstrategiaResolucion estrategiaAplicada;

    @Column(nullable = false)
    private boolean resuelto;

    @Column(name = "reserva_alternativa_id")
    private Long reservaAlternativaId;

    @Column(length = 255)
    private String mensaje;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    protected ConflictoOverbooking() {
        // requerido por JPA
    }

    public ConflictoOverbooking(Long reservaIdConflictiva, Long hotelId, String tipoHabitacion,
                                 LocalDate checkIn, LocalDate checkOut, String canalOrigen, String referenciaExterna) {
        this.reservaIdConflictiva = reservaIdConflictiva;
        this.hotelId = hotelId;
        this.tipoHabitacion = tipoHabitacion;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.canalOrigen = canalOrigen;
        this.referenciaExterna = referenciaExterna;
        this.resuelto = false;
        this.fechaCreacion = LocalDateTime.now();
    }

    public void resolver(EstrategiaResolucion estrategia, Long reservaAlternativaId, String mensaje) {
        this.estrategiaAplicada = estrategia;
        this.reservaAlternativaId = reservaAlternativaId;
        this.mensaje = mensaje;
        this.resuelto = true;
    }

    public Long getId() { return id; }
    public Long getReservaIdConflictiva() { return reservaIdConflictiva; }
    public Long getHotelId() { return hotelId; }
    public String getTipoHabitacion() { return tipoHabitacion; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public String getCanalOrigen() { return canalOrigen; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public EstrategiaResolucion getEstrategiaAplicada() { return estrategiaAplicada; }
    public boolean isResuelto() { return resuelto; }
    public Long getReservaAlternativaId() { return reservaAlternativaId; }
    public String getMensaje() { return mensaje; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}