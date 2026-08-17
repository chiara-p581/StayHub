package com.stayhub.canalesexternos.client.ota;
import com.stayhub.canalesexternos.config.ConfiguracionCanales;
import com.stayhub.canalesexternos.dto.*;
import com.stayhub.canalesexternos.exception.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;
import java.util.List;
@ApplicationScoped
public class OtaRestClientImpl implements OtaRestClient {
    @Inject ConfiguracionCanales configuracion;
    public void publicarDisponibilidad(Canal canal, List<DisponibilidadDTO> datos) { publicar(canal, "inventario", datos); }
    public void publicarTarifas(Canal canal, List<TarifaDTO> datos) { publicar(canal, "tarifas", datos); }
    private void publicar(Canal canal, String ruta, Object cuerpo) {
        try (Client client = ClientBuilder.newClient()) {
            Invocation.Builder request = client.target(configuracion.urlOta(canal)).path(ruta).request(MediaType.APPLICATION_JSON_TYPE);
            String token = configuracion.tokenOta(canal);
            if (token != null && !token.isBlank()) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            try (Response response = request.put(Entity.json(cuerpo))) {
                if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL)
                    throw new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_OTA,
                            "La OTA " + canal + " respondió HTTP " + response.getStatus());
            }
        } catch (CanalExternoException ex) { throw ex; }
        catch (RuntimeException ex) { throw new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_OTA,
                "No se pudo comunicar con la OTA " + canal, ex); }
    }
}
