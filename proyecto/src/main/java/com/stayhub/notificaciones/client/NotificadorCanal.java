package com.stayhub.notificaciones.client;

import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;

/**
 * Estrategia de envío por canal (GoF Strategy / GRASP Polimorfismo).
 * Cada implementación dice qué canal atiende, así el servicio no necesita
 * un switch: para sumar WhatsApp alcanza con una clase nueva (OCP).
 */
public interface NotificadorCanal {
    CanalNotificacion canal();

    void enviar(SolicitudNotificacionDTO solicitud);
}