package com.stayhub.integracionpms.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/**
 * Reserva tal como la ve el PMS legado en el contrato SOAP (complexType
 * "reserva" del WSDL). Las fechas viajan como texto AAAA-MM-DD.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "reserva", propOrder = {"id", "canal", "referenciaExterna", "hotelId", "tipoHabitacion", "cantidadHabitaciones", "checkIn", "checkOut", "huespedNombre", "huespedApellido", "huespedEmail", "precioTotal", "moneda", "estado"})
public class ReservaPmsDTO {

    private Long id;
    private String canal;
    private String referenciaExterna;
    private Long hotelId;
    private String tipoHabitacion;
    private int cantidadHabitaciones;
    private String checkIn;
    private String checkOut;
    private String huespedNombre;
    private String huespedApellido;
    private String huespedEmail;
    private BigDecimal precioTotal;
    private String moneda;
    private String estado;

    public ReservaPmsDTO() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getReferenciaExterna() { return referenciaExterna; }
    public void setReferenciaExterna(String referenciaExterna) { this.referenciaExterna = referenciaExterna; }

    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }

    public String getTipoHabitacion() { return tipoHabitacion; }
    public void setTipoHabitacion(String tipoHabitacion) { this.tipoHabitacion = tipoHabitacion; }

    public int getCantidadHabitaciones() { return cantidadHabitaciones; }
    public void setCantidadHabitaciones(int cantidadHabitaciones) { this.cantidadHabitaciones = cantidadHabitaciones; }

    public String getCheckIn() { return checkIn; }
    public void setCheckIn(String checkIn) { this.checkIn = checkIn; }

    public String getCheckOut() { return checkOut; }
    public void setCheckOut(String checkOut) { this.checkOut = checkOut; }

    public String getHuespedNombre() { return huespedNombre; }
    public void setHuespedNombre(String huespedNombre) { this.huespedNombre = huespedNombre; }

    public String getHuespedApellido() { return huespedApellido; }
    public void setHuespedApellido(String huespedApellido) { this.huespedApellido = huespedApellido; }

    public String getHuespedEmail() { return huespedEmail; }
    public void setHuespedEmail(String huespedEmail) { this.huespedEmail = huespedEmail; }

    public BigDecimal getPrecioTotal() { return precioTotal; }
    public void setPrecioTotal(BigDecimal precioTotal) { this.precioTotal = precioTotal; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
