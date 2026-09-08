package com.stayhub.canalesexternos.client.pms;
public class DisponibilidadPms {
    public String tipoHabitacion; public String desde; public String hasta; public int unidades;
    public DisponibilidadPms() { }
    public DisponibilidadPms(String tipo, String desde, String hasta, int unidades) {
        this.tipoHabitacion=tipo; this.desde=desde; this.hasta=hasta; this.unidades=unidades;
    }
}
