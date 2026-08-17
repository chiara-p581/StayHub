package com.stayhub.canalesexternos.client.pms;
import jakarta.jws.*;
import java.util.List;
@WebService(targetNamespace=PmsSoapPort.NAMESPACE, name="PmsLegacyPort")
public interface PmsSoapPort {
    String NAMESPACE="http://pms.stayhub.com/legacy";
    @WebMethod RespuestaPms sincronizarInventario(Long hotelId, List<DisponibilidadPms> disponibilidad, List<TarifaPms> tarifas);
}
