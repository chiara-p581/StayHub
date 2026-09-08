package com.stayhub.hoteles.model;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tipo_habitacion", uniqueConstraints =
        @UniqueConstraint(name = "uk_tipo_hotel_codigo", columnNames = {"hotel_id", "codigo"}))
public class TipoHabitacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    @Column(nullable = false, length = 30)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(length = 800)
    private String descripcion;
    @Column(name = "capacidad_maxima", nullable = false)
    private int capacidadMaxima;
    @Column(nullable = false)
    private boolean activo = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tipo_habitacion_caracteristica",
            joinColumns = @JoinColumn(name = "tipo_habitacion_id"))
    @Column(name = "caracteristica", nullable = false, length = 100)
    private Set<String> caracteristicas = new LinkedHashSet<>();

    protected TipoHabitacion() { }
    public TipoHabitacion(Hotel hotel, String codigo, String nombre, String descripcion,
                          int capacidadMaxima, Set<String> caracteristicas) {
        this.hotel = hotel;
        actualizar(codigo, nombre, descripcion, capacidadMaxima, caracteristicas);
    }
    public void actualizar(String codigo, String nombre, String descripcion, int capacidadMaxima,
                           Set<String> caracteristicas) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.capacidadMaxima = capacidadMaxima;
        this.caracteristicas.clear();
        if (caracteristicas != null) this.caracteristicas.addAll(caracteristicas);
    }
    public void darDeBaja() { activo = false; }
    public Long getId() { return id; }
    public Hotel getHotel() { return hotel; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public int getCapacidadMaxima() { return capacidadMaxima; }
    public boolean isActivo() { return activo; }
    public Set<String> getCaracteristicas() { return Set.copyOf(caracteristicas); }
}
