package com.stayhub.notificaciones.service;

import com.stayhub.notificaciones.client.EmailNotificador;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.model.EnvioCorreo;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class EnvioCorreoIdempotente {

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Inject
    private EmailNotificador email;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void enviar(String clave, SolicitudNotificacionDTO solicitud) {

        // Evita que dos consumidores procesen simultáneamente la misma clave.
        // Este bloqueo es específico de PostgreSQL y dura hasta finalizar
        // la transacción.
        em.createNativeQuery(
                "select cast(pg_advisory_xact_lock(cast(:clave as bigint)) as text)"
        )
                .setParameter("clave", (long) clave.hashCode())
                .getSingleResult();

        if (em.find(EnvioCorreo.class, clave) != null) {
            return;
        }

        email.enviar(solicitud);

        em.persist(new EnvioCorreo(clave));
        em.flush();
    }
}