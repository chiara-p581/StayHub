package com.stayhub.pagos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pago_reserva", joinColumns = @JoinColumn(name = "pago_id"))
    @Column(name = "reserva_id", nullable = false)
    private List<Long> reservaIds = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "referencia_pasarela", length = 60)
    private String referenciaPasarela;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @Version
    private Long version;

    protected Pago() {
        // requerido por JPA
    }

    public Pago(Long reservaId, BigDecimal monto, String moneda) {
        this(List.of(reservaId), monto, moneda);
    }

    public Pago(List<Long> reservaIds, BigDecimal monto, String moneda) {
        this.reservaId = reservaIds.get(0);
        this.reservaIds.addAll(reservaIds);
        this.monto = monto;
        this.moneda = moneda;
        this.estado = EstadoPago.PENDIENTE;
        this.fechaPago = LocalDateTime.now();
    }

    public void aprobar(String referenciaPasarela) {
        this.estado = EstadoPago.APROBADO;
        this.referenciaPasarela = referenciaPasarela;
    }

    public void rechazar() {
        this.estado = EstadoPago.RECHAZADO;
    }

    public Long getId() { return id; }
    public Long getReservaId() { return reservaId; }
    public List<Long> getReservaIds() { return List.copyOf(reservaIds); }
    public BigDecimal getMonto() { return monto; }
    public String getMoneda() { return moneda; }
    public EstadoPago getEstado() { return estado; }
    public String getReferenciaPasarela() { return referenciaPasarela; }
    public LocalDateTime getFechaPago() { return fechaPago; }
}
