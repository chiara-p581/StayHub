package com.stayhub.notificaciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notificacion_email_enviada")
public class EnvioCorreo {

    @Id
    @Column(length = 180)
    private String clave;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    protected EnvioCorreo() {
    }

    public EnvioCorreo(String clave) {
        this.clave = clave;
        this.fecha = OffsetDateTime.now();
    }
}