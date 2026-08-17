package com.stayhub.canalesexternos.client.pms;
import java.math.BigDecimal;
public class TarifaPms {
    public String tipoHabitacion; public String desde; public String hasta; public BigDecimal importe; public String moneda;
    public TarifaPms() { }
    public TarifaPms(String tipo, String desde, String hasta, BigDecimal importe, String moneda) {
        this.tipoHabitacion=tipo; this.desde=desde; this.hasta=hasta; this.importe=importe; this.moneda=moneda;
    }
}
