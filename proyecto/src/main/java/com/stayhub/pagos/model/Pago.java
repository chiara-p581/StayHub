package com.stayhub.pagos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pago", uniqueConstraints = @UniqueConstraint(name = "uk_pago_usuario_idempotencia", columnNames = {"usuario_id", "clave_idempotencia"}))
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

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

    @Column(name = "clave_idempotencia", length = 80)
    private String claveIdempotencia;

    @Version
    private Long version;

    protected Pago() {
        // requerido por JPA
    }

    public Pago(Long reservaId, BigDecimal monto, String moneda) {
        this(null, List.of(reservaId), monto, moneda, null);
    }

    public Pago(List<Long> reservaIds, BigDecimal monto, String moneda) {
        this(null, reservaIds, monto, moneda, null);
    }

    public Pago(List<Long> reservaIds, BigDecimal monto, String moneda, String claveIdempotencia) {
        this(null, reservaIds, monto, moneda, claveIdempotencia);
    }

    public Pago(Long usuarioId, List<Long> reservaIds, BigDecimal monto, String moneda, String claveIdempotencia) {
        this.usuarioId = usuarioId;
        this.reservaId = reservaIds.get(0);
        this.reservaIds.addAll(reservaIds);
        this.monto = monto;
        this.moneda = moneda;
        this.estado = EstadoPago.PENDIENTE;
        this.fechaPago = LocalDateTime.now();
        this.claveIdempotencia = claveIdempotencia;
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
    public Long getUsuarioId() { return usuarioId; }
    public List<Long> getReservaIds() { return List.copyOf(reservaIds); }
    public BigDecimal getMonto() { return monto; }
    public String getMoneda() { return moneda; }
    public EstadoPago getEstado() { return estado; }
    public String getReferenciaPasarela() { return referenciaPasarela; }
    public LocalDateTime getFechaPago() { return fechaPago; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
}
