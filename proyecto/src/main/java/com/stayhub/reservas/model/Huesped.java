package com.stayhub.reservas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Datos del huésped, embebidos dentro de Reserva (no es una entidad propia).
 * Se mapea desde/hacia com.stayhub.canalesexternos.dto.HuespedDTO en el
 * borde del componente (ver ReservaMapper), para no acoplar la entidad JPA
 * a un tipo que pertenece a otro servicio.
 */
@Embeddable
public class Huesped {

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String apellido;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 30)
    private String telefono;

    protected Huesped() {
    }

    public Huesped(String nombre, String apellido, String email, String telefono) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.telefono = telefono;
    }

    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
}
