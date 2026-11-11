package com.stayhub.canalesexternos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Clave persistente de idempotencia: una solicitud de sincronización procesada con éxito. */
@Entity
@Table(name = "sincronizacion_procesada")
public class SincronizacionProcesada {

    @Id
    @Column(name = "solicitud_id", length = 64)
    private String solicitudId;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    protected SincronizacionProcesada() {
    }

    public SincronizacionProcesada(String solicitudId) {
        this.solicitudId = solicitudId;
        this.fecha = OffsetDateTime.now();
    }

    public String getSolicitudId() {
        return solicitudId;
    }
}