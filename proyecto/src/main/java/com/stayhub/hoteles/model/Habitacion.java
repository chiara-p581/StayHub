package com.stayhub.hoteles.model;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "habitacion", uniqueConstraints =
        @UniqueConstraint(name = "uk_habitacion_hotel_numero", columnNames = {"hotel_id", "numero"}))
public class Habitacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_habitacion_id", nullable = false)
    private TipoHabitacion tipo;
    @Column(nullable = false, length = 30)
    private String numero;
    private Integer piso;
    @Column(nullable = false)
    private boolean activa = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "habitacion_caracteristica", joinColumns = @JoinColumn(name = "habitacion_id"))
    @Column(name = "caracteristica", nullable = false, length = 100)
    private Set<String> caracteristicas = new LinkedHashSet<>();

    protected Habitacion() { }
    public Habitacion(Hotel hotel, TipoHabitacion tipo, String numero, Integer piso,
                      Set<String> caracteristicas) {
        this.hotel = hotel;
        actualizar(tipo, numero, piso, caracteristicas);
    }
    public void actualizar(TipoHabitacion tipo, String numero, Integer piso, Set<String> caracteristicas) {
        this.tipo = tipo;
        this.numero = numero;
        this.piso = piso;
        this.caracteristicas.clear();
        if (caracteristicas != null) this.caracteristicas.addAll(caracteristicas);
    }
    public void darDeBaja() { activa = false; }
    public Long getId() { return id; }
    public Hotel getHotel() { return hotel; }
    public TipoHabitacion getTipo() { return tipo; }
    public String getNumero() { return numero; }
    public Integer getPiso() { return piso; }
    public boolean isActiva() { return activa; }
    public Set<String> getCaracteristicas() { return Set.copyOf(caracteristicas); }
}
