package com.stayhub.canalesexternos.client.ota;
import com.stayhub.canalesexternos.dto.*;
import java.util.List;
public interface OtaRestClient {
    void publicarDisponibilidad(Canal canal, List<DisponibilidadDTO> disponibilidad);
    void publicarTarifas(Canal canal, List<TarifaDTO> tarifas);
}
