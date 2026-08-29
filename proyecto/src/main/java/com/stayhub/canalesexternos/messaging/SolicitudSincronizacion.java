package com.stayhub.canalesexternos.messaging;

import com.stayhub.canalesexternos.dto.Canal;
import java.io.Serializable;
import java.time.LocalDate;

public record SolicitudSincronizacion(
        TipoDestino destino, Long hotelId, Canal canal, LocalDate desde, LocalDate hasta) implements Serializable {

    public enum TipoDestino { OTA, PMS }

    public static SolicitudSincronizacion paraOta(Long hotelId, Canal canal, LocalDate desde, LocalDate hasta) {
        return new SolicitudSincronizacion(TipoDestino.OTA, hotelId, canal, desde, hasta);
    }

    public static SolicitudSincronizacion paraPms(Long hotelId, LocalDate desde, LocalDate hasta) {
        return new SolicitudSincronizacion(TipoDestino.PMS, hotelId, null, desde, hasta);
    }
}
