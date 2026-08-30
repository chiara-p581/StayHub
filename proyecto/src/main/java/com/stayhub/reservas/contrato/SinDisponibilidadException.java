package com.stayhub.reservas.contrato;

import jakarta.ejb.ApplicationException;

/**
 * Lanzada por GestionDeDisponibilidadPort.crearHold cuando no hay cupo.
 *
 * @ApplicationException es necesaria porque esta excepción cruza un límite
 * entre dos EJBs distintos (la lanza ServicioDeInventarioYTarifasImpl, la
 * atrapa ServicioDeReservasImpl). Sin esta anotación, el contenedor EJB
 * trata cualquier RuntimeException "normal" como un error grave del
 * sistema y la envuelve en jakarta.ejb.EJBException antes de propagarla,
 * por lo que un catch (SinDisponibilidadException ex) del lado de quien
 * llama nunca la atrapa (terminaba en un 500 en vez de rechazar prolijamente
 * la reserva). @ApplicationException le dice al contenedor que esto es un
 * resultado de negocio esperado, no una falla del sistema, y que la deje
 * pasar tal cual.
 *
 * rollback = false: no hay que deshacer nada cuando pasa esto -- todo lo
 * contrario, ServicioDeReservasImpl atrapa esta excepción y necesita seguir
 * usando la misma transacción para guardar la reserva como RECHAZADA. Con
 * rollback = true la transacción queda marcada "solo para deshacer" apenas
 * se lanza la excepción, y el guardado posterior falla con
 * STATUS_MARKED_ROLLBACK aunque el catch la haya atrapado bien.
 */
@ApplicationException(rollback = false)
public class SinDisponibilidadException extends RuntimeException {
    public SinDisponibilidadException(String mensaje) {
        super(mensaje);
    }
}
