package com.stayhub.reservas.api;

import jakarta.ejb.EJBTransactionRolledbackException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;

/**
 * En este proyecto, cuando un EJB @Stateless termina su método y el
 * contenedor recién ahí confirma la transacción (comportamiento normal con
 * transacciones manejadas por el contenedor), un conflicto de concurrencia
 * detectado por JPA (OptimisticLockException, por el campo @Version de
 * Reserva) no llega "directo" a quien llama -llega envuelta en
 * EJBTransactionRolledbackException, que es la excepción que define la
 * especificación EJB justamente para este caso (falla del sistema al
 * confirmar la transacción). Este mapper la desenvuelve: si la causa es un
 * conflicto de concurrencia, responde 409 igual que OptimisticLockExceptionMapper;
 * para cualquier otra causa devuelve un error genérico, sin exponer detalles
 * internos en la respuesta HTTP.
 */
@Provider
public class EJBTransactionRolledbackExceptionMapper implements ExceptionMapper<EJBTransactionRolledbackException> {
    @Override
    public Response toResponse(EJBTransactionRolledbackException ex) {
        if (esConflictoDeConcurrencia(ex.getCause())) {
            return Response.status(409)
                    .entity(new ErrorDTO("CONFLICTO_DE_CONCURRENCIA",
                            "La reserva fue modificada por otro pedido al mismo tiempo; volvé a consultarla e intentá de nuevo.",
                            OffsetDateTime.now()))
                    .type(MediaType.APPLICATION_JSON).build();
        }
        return Response.status(500).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO("ERROR_INTERNO", "An unexpected error occurred.", OffsetDateTime.now()))
                .build();
    }

    private boolean esConflictoDeConcurrencia(Throwable causa) {
        for (Throwable t = causa; t != null; t = t.getCause()) {
            if (t instanceof OptimisticLockException) {
                return true;
            }
        }
        return false;
    }
}
