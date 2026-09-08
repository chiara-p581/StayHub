package com.stayhub.hoteles.model;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "hotel")
public class Hotel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false, length = 180)
    private String direccion;
    @Column(nullable = false, length = 100)
    private String ciudad;
    @Column(nullable = false, length = 100)
    private String pais;
    @Column(length = 1000)
    private String descripcion;
    @Column(nullable = false)
    private boolean activo = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hotel_servicio", joinColumns = @JoinColumn(name = "hotel_id"))
    @Column(name = "servicio", nullable = false, length = 80)
    private Set<String> servicios = new LinkedHashSet<>();

    protected Hotel() { }

    public Hotel(String nombre, String direccion, String ciudad, String pais,
                 String descripcion, Set<String> servicios) {
        actualizar(nombre, direccion, ciudad, pais, descripcion, servicios);
    }

    public void actualizar(String nombre, String direccion, String ciudad, String pais,
                           String descripcion, Set<String> servicios) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.pais = pais;
        this.descripcion = descripcion;
        this.servicios.clear();
        if (servicios != null) this.servicios.addAll(servicios);
    }

    public void darDeBaja() { activo = false; }
    public void reactivar() { activo = true; }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getCiudad() { return ciudad; }
    public String getPais() { return pais; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
    public Set<String> getServicios() { return Set.copyOf(servicios); }
}
