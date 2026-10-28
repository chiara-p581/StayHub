package com.stayhub.notificaciones.service;

import com.stayhub.seguridad.*;
import com.stayhub.notificaciones.client.NotificadorCanal;
import com.stayhub.notificaciones.contrato.ServicioDeNotificaciones;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.ResultadoEnvioNotificacionDTO;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.exception.CodigoErrorNotificacion;
import com.stayhub.notificaciones.exception.NotificacionException;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Locale;

/**
 * Envío manual de notificaciones (lo usa el panel de administración).
 *
 * <p>Seguridad: solo ADMIN, controlado por el contenedor con
 * {@code @RolesAllowed} (SEC-W07), además del filtro HTTP. Los correos
 * automáticos (bienvenida, pago, contraseña) no pasan por acá: los mandan los
 * MDB a través de {@code EnvioCorreoIdempotente}, por eso no se ven afectados.</p>
 *
 * <p>Diseño: el servicio ya no conoce las clases concretas (antes tenía un
 * switch con Email/Sms/Push). Recibe todas las estrategias
 * {@link NotificadorCanal} y elige la que atiende el canal pedido.
 * SOLID: abierto/cerrado e inversión de dependencias.
 * GRASP: Polimorfismo y Variaciones protegidas.</p>
 */
@Stateless
@DeclareRoles({Roles.HUESPED, Roles.ADMIN})
@RolesAllowed(Roles.ADMIN)
public class ServicioDeNotificacionesImpl implements ServicioDeNotificaciones {
    private static final LimitadorSolicitudes ENVIOS = new LimitadorSolicitudes();

    @Inject
    @Any
    Instance<NotificadorCanal> notificadores;

    @Override
    public ResultadoEnvioNotificacionDTO enviar(SolicitudNotificacionDTO solicitud) {
        validar(solicitud);
        Limites.texto(solicitud.destinatario(), 120, true);
        Limites.texto(solicitud.asunto(), 200, true);
        Limites.texto(solicitud.mensaje(), 4000, true);

        String clave = solicitud.canalPreferido() + ":" + solicitud.destinatario().toLowerCase(Locale.ROOT);
        if (!ENVIOS.permitir(clave, 5, System.currentTimeMillis()))
            throw new SeguridadException(429, "Cuota de notificaciones excedida");

        NotificadorCanal notificador = notificadorPara(solicitud.canalPreferido());
        try {
            notificador.enviar(solicitud);
        } catch (RuntimeException ex) {
            throw new NotificacionException(CodigoErrorNotificacion.ERROR_ENVIO,
                    "No se pudo enviar la notificación por " + solicitud.canalPreferido(), ex);
        }

        return ResultadoEnvioNotificacionDTO.exitoso(solicitud.canalPreferido());
    }

    /** Busca la estrategia del canal; si no hay ninguna, falla de forma segura con 400. */
    NotificadorCanal notificadorPara(CanalNotificacion canal) {
        for (NotificadorCanal candidato : notificadores) {
            if (candidato.canal() == canal) return candidato;
        }
        throw new NotificacionException(CodigoErrorNotificacion.CANAL_NO_SOPORTADO,
                "No hay un notificador configurado para el canal " + canal);
    }

    private void validar(SolicitudNotificacionDTO solicitud) {
        if (solicitud == null || solicitud.destinatario() == null || solicitud.destinatario().isBlank()
                || solicitud.canalPreferido() == null || solicitud.tipoEvento() == null)
            throw new NotificacionException(CodigoErrorNotificacion.SOLICITUD_INVALIDA,
                    "Faltan datos obligatorios (destinatario / canal / tipoEvento)");
    }
}