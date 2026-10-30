package com.stayhub.canalesexternos.client.pms;
import com.stayhub.canalesexternos.config.ConfiguracionCanales;
import com.stayhub.canalesexternos.dto.*;
import com.stayhub.canalesexternos.exception.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.soap.SOAPBinding;
import java.util.List;
import javax.xml.namespace.QName;
@ApplicationScoped
public class PmsLegacyClientImpl implements PmsLegacyClient {
    @Inject ConfiguracionCanales configuracion;
    public RespuestaPms sincronizar(Long hotelId, List<DisponibilidadDTO> disponibilidad, List<TarifaDTO> tarifas) {
        try {
            QName servicio = new QName(PmsSoapPort.NAMESPACE,"PmsLegacyService");
            QName puerto = new QName(PmsSoapPort.NAMESPACE,"PmsLegacyServicePort");
            Service service = Service.create(servicio);
            service.addPort(puerto, SOAPBinding.SOAP11HTTP_BINDING, configuracion.endpointPms());
            PmsSoapPort port=service.getPort(puerto, PmsSoapPort.class);
            if (port instanceof BindingProvider binding) {
                binding.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", configuracion.timeoutConexionMs());
                binding.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", configuracion.timeoutLecturaMs());
                binding.getRequestContext().put("com.sun.xml.ws.connect.timeout", (int) configuracion.timeoutConexionMs());
                binding.getRequestContext().put("com.sun.xml.ws.request.timeout", (int) configuracion.timeoutLecturaMs());
            }
            var inventario=disponibilidad.stream().map(d->new DisponibilidadPms(d.tipoHabitacion(),d.desde().toString(),d.hasta().toString(),d.unidadesDisponibles())).toList();
            var precios=tarifas.stream().map(t->new TarifaPms(t.tipoHabitacion(),t.desde().toString(),t.hasta().toString(),t.importe(),t.moneda())).toList();
            RespuestaPms respuesta=port.sincronizarInventario(hotelId,inventario,precios);
            if (respuesta==null) throw new CanalExternoException(CodigoErrorCanal.RESPUESTA_EXTERNA_INVALIDA,"El PMS devolvió una respuesta vacía");
            return respuesta;
        } catch (CanalExternoException ex) { throw ex; }
        catch (Exception ex) { throw new CanalExternoException(CodigoErrorCanal.ERROR_COMUNICACION_PMS,"No se pudo sincronizar con el PMS legado",ex); }
    }
}
