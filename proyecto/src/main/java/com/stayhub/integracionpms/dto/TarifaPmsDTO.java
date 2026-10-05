package com.stayhub.integracionpms.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/**
 * Tarifa de un tipo de habitación en un período (complexType "tarifa"
 * del WSDL).
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tarifa", propOrder = {"hotelId", "tipoHabitacion", "desde", "hasta", "importe", "moneda"})
public class TarifaPmsDTO {

    private Long hotelId;
    private String tipoHabitacion;
    private String desde;
    private String hasta;
    private BigDecimal importe;
    private String moneda;

    public TarifaPmsDTO() { }

    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }

    public String getTipoHabitacion() { return tipoHabitacion; }
    public void setTipoHabitacion(String tipoHabitacion) { this.tipoHabitacion = tipoHabitacion; }

    public String getDesde() { return desde; }
    public void setDesde(String desde) { this.desde = desde; }

    public String getHasta() { return hasta; }
    public void setHasta(String hasta) { this.hasta = hasta; }

    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
}
