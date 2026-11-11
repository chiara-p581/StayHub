# DLQ, transacciones e idempotencia

## Entrega atómica entre PostgreSQL y JMS

La configuración Docker de StayHub usa una fuente de datos PostgreSQL XA (`PGXADataSource`) y `java:/JmsXA`.
Los servicios EJB ejecutan la escritura JPA y la publicación JMS dentro de la
misma transacción JTA. PostgreSQL se inicia con transacciones preparadas
habilitadas. Si falla la persistencia o el envío al broker, WildFly revierte
ambos recursos; no queda un dato confirmado sin su evento obligatorio.

Esto requiere que ambos recursos estén efectivamente configurados como XA.
El WildFly local se migró de JDBC JTA a `PGXADataSource` el 9/11/2026, después
de habilitar transacciones preparadas en Aiven. Se verificaron commit de ambos
recursos y rollback ante fallos reales de base y broker; ver
[KAN-94](tickets/KAN-94-CONSISTENCIA-XA.md).

## Política de reintentos y DLQ

Los destinos de usuarios, pagos, recuperación de contraseña, overbooking y
sincronización de canales realizan cinco intentos, separados por 10 segundos.
Después, Artemis mueve el mensaje a `StayHubDLQ`. El consumidor registra en el
log el `JMSMessageID`, `JMSCorrelationID`, número de entrega y el tipo saneado
de la excepción, sin registrar el cuerpo ni datos personales.

### Reproceso seguro

1. Corregir primero la causa funcional o de infraestructura.
2. Inspeccionar la DLQ sin extraer el cuerpo:

   ```text
   /subsystem=messaging-activemq/server=default/jms-queue=StayHubDLQ:list-messages
   ```

3. Verificar en un único mensaje `JMSMessageID`, `JMSCorrelationID`,
   `_AMQ_ORIG_ADDRESS`, `_AMQ_ORIG_QUEUE` y el incidente asociado en el log.
4. En WildFly 41, devolver solamente ese mensaje a su cola original mediante
   `move-message`, usando el `JMSMessageID` completo y el nombre lógico de cola
   (sin prefijo `jms.queue.`). Ejemplo para sincronización:

   ```text
   /subsystem=messaging-activemq/server=default/jms-queue=StayHubDLQ:move-message(message-id="ID:identificador-del-mensaje",other-queue-name="CanalesExternosSincronizaciones",reject-duplicates=true)
   ```

5. Confirmar que desapareció de la DLQ y que el efecto de negocio existe una
   sola vez. Los consumidores de correo registran una clave persistente, por lo
   que una reentrega no vuelve a enviar el mismo correo.

No se debe mover toda la cola: mezclar incidentes puede
reintroducir mensajes cuya causa todavía no fue corregida.

La prueba real verificó cinco entregas fallidas con correlación
`stayhub-dlq-probe-20261108`, traslado a DLQ y reproceso individual tras conectar
el mock OTA. Se recibió una actualización de inventario y otra de tarifas.
La operación administrativa `retry-message` no está expuesta por WildFly 41;
el procedimiento anterior usa la operación disponible y comprobada.

## Idempotency-Key

`POST /api/reservas`, `POST /api/pagos` y `POST /api/pagos/lote` aceptan el
header `Idempotency-Key` (16 a 80 caracteres: letras, números, `.`, `_`, `:`,
`-`). En pagos se mantiene temporalmente el campo JSON `claveIdempotencia`,
pero, si se envían ambos valores, deben coincidir.

La clave se guarda asociada al usuario y tiene una restricción única en
PostgreSQL. Repetir el pedido con la misma clave devuelve el recurso original:
no crea otra reserva, no cobra otra vez y no publica eventos duplicados.
