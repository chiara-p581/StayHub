package com.stayhub.integracionpms.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Cupo disponible de un tipo de habitación en un período (complexType
 * "disponibilidad" del WSDL).
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "disponibilidad", propOrder = {"hotelId", "tipoHabitacion", "desde", "hasta", "unidadesDisponibles"})
public class DisponibilidadPmsDTO {

    private Long hotelId;
    private String tipoHabitacion;
    private String desde;
    private String hasta;
    private int unidadesDisponibles;

    public DisponibilidadPmsDTO() { }

    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }

    public String getTipoHabitacion() { return tipoHabitacion; }
    public void setTipoHabitacion(String tipoHabitacion) { this.tipoHabitacion = tipoHabitacion; }

    public String getDesde() { return desde; }
    public void setDesde(String desde) { this.desde = desde; }

    public String getHasta() { return hasta; }
    public void setHasta(String hasta) { this.hasta = hasta; }

    public int getUnidadesDisponibles() { return unidadesDisponibles; }
    public void setUnidadesDisponibles(int unidadesDisponibles) { this.unidadesDisponibles = unidadesDisponibles; }
}
