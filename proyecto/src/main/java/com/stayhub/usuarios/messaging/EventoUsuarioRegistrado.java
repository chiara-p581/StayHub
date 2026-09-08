package com.stayhub.usuarios.messaging;

import java.io.Serializable;

/**
 * Evento publicado cuando se registra un usuario nuevo. Igual que
 * EventoPagoAprobado en el componente Pagos: varios interesados
 * (Notificaciones para el mail de bienvenida, y a futuro lo que haga
 * falta) pueden suscribirse sin que ServicioDeUsuarios los conozca.
 */
public record EventoUsuarioRegistrado(
        Long usuarioId,
        String email,
        String nombre,
        String rol) implements Serializable { }