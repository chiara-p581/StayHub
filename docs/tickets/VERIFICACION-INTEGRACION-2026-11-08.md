# Verificación local de integración — 8 de noviembre de 2026

Entorno: WildFly 41 local, WAR final, PostgreSQL Aiven y mocks REST/SOAP
ejecutados en JVM separadas. No se utilizó Docker.

## DLQ y recuperación

Se aplicó `config/notificaciones-reintentos.cli` al WildFly local.
Una sonda temporal sin endpoints HTTP publicó una solicitud serializada válida
en `CanalesExternosSincronizaciones` con correlación
`stayhub-dlq-probe-20261108`. La integración OTA todavía no tenía su URL
configurada, por lo que falló sin enviar tráfico a terceros.

Entregas registradas en el log local:

| Entrega | Hora local |
|---|---|
| 1 | 17:19:43 |
| 2 | 17:19:54 |
| 3 | 17:20:05 |
| 4 | 17:20:16 |
| 5 | 17:20:26 |

El broker trasladó el mensaje a `StayHubDLQ` conservando correlación y origen.
Se configuraron temporalmente URLs de mocks en 127.0.0.1, permitiendo HTTP
solamente para la prueba local. Se ejecutó:

```text
/subsystem=messaging-activemq/server=default/jms-queue=StayHubDLQ:move-message(message-id="ID:bbac1fcb-dbb1-11f1-92bf-9e809424ac93",other-queue-name="CanalesExternosSincronizaciones",reject-duplicates=true)
```

La operación devolvió `true`. El mock recibió un PUT de inventario y otro de
tarifas. Una consulta filtrada por correlación devolvió cero mensajes en DLQ y
la cola de sincronización quedó vacía. La API JMS de WildFly exige el
`JMSMessageID`, no el identificador numérico interno de Artemis.

## Sincronización OTA/PMS desde la interfaz

La usuaria inició sesión ADMIN en Safari sin compartir credenciales.
Para el hotel 2, del 15 al 17/11/2026, la interfaz aceptó las solicitudes de
Booking y PMS como `Queued`. Los mocks confirmaron recepción REST y SOAP;
el PMS recibió dos registros de disponibilidad y dos tarifas. Esto verifica
el envío, la cola, el consumidor y los clientes de integración reales.

## Overbooking y notificación

Con autorización explícita se envió desde la interfaz el conflicto ficticio
`qa-overbooking-20261108-final`, reserva conflictiva 999981, hotel 2,
STANDARD, una habitación, del 13 al 15/12/2026, destinatario ficticio `.invalid`.

La pantalla mostró `Issue resolved`, `REUBICACION` y alternativa `SUITE`.
La consulta de base confirmó conflicto 4, `resuelto=true`, alternativa SUITE
y hold `f6cbb9a0-15a1-4c8b-9b09-c61435d864ae`.
La cola `OverbookingNotificaciones` registró `messages-added=1` y quedó con
`message-count=0`. La tabla `notificacion_email_enviada` contiene la clave
`overbooking:ID:a1509393-dbb2-11f1-92bf-9e809424ac93`.
Esto acredita publicación, consumo y registro del envío; no acredita recepción
en un buzón real. Se conservaron el conflicto y el hold ficticios como evidencia.

## Carga del WAR final

50 usuarios durante cinco minutos, finalizada 20:23 UTC: p95 global 24,09 ms,
0% errores HTTP y 100% checks. Todos los umbrales por endpoint aprobaron.
Ver [KAN-98](KAN-98-PRUEBA-CARGA.md) y `performance/reports/k6-summary.json`.

## XA: impedimento comprobado

Aiven respondió `max_prepared_transactions=0`, `max_connections=20` y cero
transacciones preparadas pendientes. El usuario de conexión no es superusuario.
Una transacción de prueba sin cambios de datos recibió SQLState `55000` al
ejecutar PREPARE y luego se revirtió. La migración a XA y la prueba de rollback
distribuido quedan pendientes de habilitar transacciones preparadas en Aiven.
No se sustituyó esta limitación con una afirmación de atomicidad no probada.

Actualización del 9/11: el bloqueo se resolvió. Aiven tiene el parámetro en 20,
WildFly usa PGXADataSource y pasó la sonda de commit/rollback con fallos reales
de PostgreSQL y Artemis. Ver la evidencia y su alcance en
[KAN-94](KAN-94-CONSISTENCIA-XA.md).

## Retirada del instrumental

Se retiró el WAR temporal `stayhub-jms-probe.war`, se detuvieron ambos mocks y
se eliminaron las cuatro propiedades temporales de integración local. La
política DLQ de entrega permanece aplicada. El dominio exclusivo
`StayHubSecurity` y las restricciones del dominio `other` se conservan.
