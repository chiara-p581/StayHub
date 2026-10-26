package com.stayhub.pmslegado;

import java.math.BigDecimal;

/** Mismo formato que com.stayhub.canalesexternos.client.pms.TarifaPms. */
public class TarifaPms {
    public String tipoHabitacion;
    public String desde;
    public String hasta;
    public BigDecimal importe;
    public String moneda;

    public TarifaPms() { }
}
