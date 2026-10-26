package com.stayhub.canalesexternos.client.pms;
import com.stayhub.canalesexternos.config.ConfiguracionCanales;
import com.stayhub.canalesexternos.dto.*;
import com.stayhub.canalesexternos.exception.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.BindingProvider;
import java.net.URL;
import java.util.List;
import javax.xml.namespace.QName;
@ApplicationScoped
public class PmsLegacyClientImpl implements PmsLegacyClient {
    @Inject ConfiguracionCanales configuracion;
    public RespuestaPms sincronizar(Long hotelId, List<DisponibilidadDTO> disponibilidad, List<TarifaDTO> tarifas) {
        try {
            Service service=Service.create(new URL(configuracion.wsdlPms()), new QName(PmsSoapPort.NAMESPACE,"PmsLegacyService"));
            PmsSoapPort port=service.getPort(PmsSoapPort.class);
            if (port instanceof BindingProvider binding) {
                // Nunca se confía en la dirección publicada por un WSDL remoto:
                // podría convertir una integración permitida en una segunda salida SSRF.
                binding.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, configuracion.endpointPms());
                binding.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", configuracion.timeoutConexionMs());
                binding.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", configuracion.timeoutLecturaMs());
            }
            var inventario=disponibilidad.stream().map(d->new DisponibilidadPms(d.tipoHabitacion(),d.desde().toString(),d.hasta().toString(),d.unidadesDisponibles())).toList();
            var precios=tarifas.stream().map(t->new TarifaPms(t.tipoHabitacion(),t.desde().toString(),t.hasta().toString(),t.importe(),t.moneda())).toList();
            RespuestaPms respuesta=port.sincronizarInventario(hotelId,inventario,precios);
            if (respuesta==null || respuesta.mensaje != null &&
                    (respuesta.mensaje.length() > 500 || respuesta.mensaje.chars().anyMatch(c -> c < 0x20 && c != '\n' && c != '\r' && c != '\t')))
                throw new CanalExternoException(CodigoErrorCanal.RESPUESTA_EXTERNA_INVALIDA,"El PMS devolvió una respuesta inválida");
            return respuesta;
        } catch (CanalExternoException ex) { throw ex; }
        catch (Exception ex) { throw new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_PMS,"No se pudo sincronizar con el PMS legado",ex); }
    }
}
