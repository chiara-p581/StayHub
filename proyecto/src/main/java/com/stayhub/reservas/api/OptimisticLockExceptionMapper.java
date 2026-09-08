package com.stayhub.reservas.api;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;

/**
 * Traduce un conflicto de edición concurrente (dos pedidos modificando la
 * misma reserva -entidad Reserva, campo @Version- casi al mismo tiempo) a
 * un 409 Conflict prolijo, en vez de dejar que se vea como un error interno
 * genérico.
 *
 * Nota: en el flujo típico de este proyecto (transacción manejada por el
 * contenedor, un solo EJB, sin flush explícito) esta excepción concreta
 * suele detectarse recién al confirmar la transacción -después de que el
 * método del EJB ya devolvió-, momento en el que el contenedor la entrega
 * envuelta como jakarta.ejb.EJBTransactionRolledbackException. Por eso
 * también existe EJBTransactionRolledbackExceptionMapper, que desenvuelve
 * ese caso. Este mapper cubre el caso en que la excepción SÍ llega directa
 * (por ejemplo, si en el futuro se agrega un em.flush() explícito).
 */
@Provider
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {
    @Override
    public Response toResponse(OptimisticLockException ex) {
        return Response.status(409)
                .entity(new ErrorDTO("CONFLICTO_DE_CONCURRENCIA",
                        "La reserva fue modificada por otro pedido al mismo tiempo; volvé a consultarla e intentá de nuevo.",
                        OffsetDateTime.now()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
