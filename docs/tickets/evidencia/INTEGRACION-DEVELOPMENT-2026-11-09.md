# Evidencia de integración con development — 9/11/2026

## JMS: mensaje inválido y solicitud válida recuperable

```text
2026-11-09 19:04:58,398 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-63 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:9a5f40fc-dc89-11f1-92bf-9e809424ac93 correlationId=qa-invalid-jms-development-20261109 delivery=1 cause=MessageFormatException
2026-11-09 19:05:08,426 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-63 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:9a5f40fc-dc89-11f1-92bf-9e809424ac93 correlationId=qa-invalid-jms-development-20261109 delivery=2 cause=MessageFormatException
2026-11-09 19:05:18,466 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-63 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:9a5f40fc-dc89-11f1-92bf-9e809424ac93 correlationId=qa-invalid-jms-development-20261109 delivery=3 cause=MessageFormatException
2026-11-09 19:05:28,487 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-63 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:9a5f40fc-dc89-11f1-92bf-9e809424ac93 correlationId=qa-invalid-jms-development-20261109 delivery=4 cause=MessageFormatException
2026-11-09 19:05:38,520 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-63 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:9a5f40fc-dc89-11f1-92bf-9e809424ac93 correlationId=qa-invalid-jms-development-20261109 delivery=5 cause=MessageFormatException
2026-11-09 19:32:00,258 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-67 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:607f49c0-dc8d-11f1-92bf-9e809424ac93 correlationId=qa-dlq-retry-development-20261109 delivery=1 cause=CanalExternoException
2026-11-09 19:32:11,923 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-67 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:607f49c0-dc8d-11f1-92bf-9e809424ac93 correlationId=qa-dlq-retry-development-20261109 delivery=2 cause=CanalExternoException
2026-11-09 19:32:23,036 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-67 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:607f49c0-dc8d-11f1-92bf-9e809424ac93 correlationId=qa-dlq-retry-development-20261109 delivery=3 cause=CanalExternoException
2026-11-09 19:32:34,084 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-67 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:607f49c0-dc8d-11f1-92bf-9e809424ac93 correlationId=qa-dlq-retry-development-20261109 delivery=4 cause=CanalExternoException
2026-11-09 19:32:45,075 WARNING [com.stayhub.notificaciones.messaging.FalloJms] (Thread-67 (activemq-client-global)) jms_failure consumidor=sincronizacion-canales messageId=ID:607f49c0-dc8d-11f1-92bf-9e809424ac93 correlationId=qa-dlq-retry-development-20261109 delivery=5 cause=CanalExternoException
```

El mensaje inválido llegó a DLQ conservando correlación y fue retirado por su ID exclusivo. El marcador de contenido no apareció en el log. La solicitud válida llegó a DLQ por ausencia del mock; move-message devolvió true al restaurarlo, el mock recibió inventario y tarifas y la consulta filtrada posterior devolvió cero mensajes.

## XA

```text
2026-11-09 19:28:57,627 INFO  [stdout] (ServerService Thread Pool -- 221) XA_PROBE PASS commitBoth
2026-11-09 19:28:58,558 INFO  [stdout] (ServerService Thread Pool -- 221) XA_PROBE PASS actual DB failure: EJBException
2026-11-09 19:28:59,638 INFO  [stdout] (ServerService Thread Pool -- 221) XA_PROBE PASS actual broker rejection: ActiveMQAddressFullException
2026-11-09 19:29:00,632 INFO  [stdout] (ServerService Thread Pool -- 221) XA_PROBE PASS controlled application rollback
2026-11-09 19:29:01,192 INFO  [stdout] (ServerService Thread Pool -- 221) XA_PROBE PASS final-state: DB only id=1; JMS only commit message
2026-11-09 19:29:02,300 INFO  [stdout] (ServerService Thread Pool -- 221) XA_PROBE CLEANUP table dropped and test queues drained
```

Se retiró la sonda y sus colas exclusivas. Consulta posterior: cero transacciones preparadas pendientes.

## E2E HTTP

```text
  ✓   1 tests/session-security.spec.cjs:73:3 › Seguridad de sesiones HTTP › una cookie de sesión manipulada no autentica al cliente (4.2s)
  ✓   2 tests/session-security.spec.cjs:84:3 › Seguridad de sesiones HTTP › el login reemplaza el identificador de sesión previo (3ms)
  ✓   3 tests/session-security.spec.cjs:94:3 › Seguridad de sesiones HTTP › la sesión de un usuario no permite consultar el perfil de otro (8.4s)
  ✓   4 tests/session-security.spec.cjs:99:3 › Seguridad de sesiones HTTP › el logout invalida la sesión y evita su reutilización (15.4s)
  ✓   5 tests/stayhub-critical.spec.cjs:135:3 › StayHub — cinco recorridos críticos › registro, login, carrito, reserva y pago persisten el resultado (1.5m)
  ✓   6 tests/stayhub-critical.spec.cjs:189:3 › StayHub — cinco recorridos críticos › cancelar una reserva devuelve el cupo al inventario (1.1m)
  ✓   7 tests/stayhub-critical.spec.cjs:221:3 › StayHub — cinco recorridos críticos › una reserva OTA firmada se procesa una sola vez y queda confirmada (19.5s)
  ✓   8 tests/tickets-idempotency-acceptance.spec.cjs:68:3 › KAN-95 — aceptación de idempotencia en HTTP real › reserva: concurrencia y repetición conservan cuerpo, ubicación y un recurso por usuario (33.5s)
  ✓   9 tests/tickets-idempotency-acceptance.spec.cjs:85:3 › KAN-95 — aceptación de idempotencia en HTTP real › pago: concurrencia no duplica cobro y la misma clave se aísla por usuario y de reservas (35.8s)
  ✓  10 tests/tickets-idempotency-acceptance.spec.cjs:103:3 › KAN-95 — aceptación de idempotencia en HTTP real › reutilizar una clave de pago para otra operación se rechaza sin devolver un pago ajeno al pedido (29.1s)
  ✓  11 tests/tickets-idempotency-acceptance.spec.cjs:115:3 › KAN-95 — aceptación de idempotencia en HTTP real › pago por lote: repetición concurrente devuelve el mismo resultado completo y confirma cada reserva una vez (21.8s)
  11 passed (6.8m)
```

## ADMIN con MFA

La usuaria completó personalmente el alta de MFA y la autenticación. El panel ADMIN cargó sus reservas. Para hotel 2 del 19 al 21/11/2026, Booking y PMS fueron recibidos por mocks REST/SOAP en loopback. PMS confirmó dos registros de disponibilidad y dos tarifas.

Overbooking ficticio: referencia qa-overbooking-development-20261109, conflicto 5, resuelto=true, alternativa SUITE y hold 4c17d8c1-51f6-4cbb-a422-22475f0a159b. La cola de notificaciones del despliegue registró un mensaje agregado y cero pendientes. Se conservaron el conflicto y el hold de QA. Esto no acredita recepción en un buzón externo.
