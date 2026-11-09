# Informe de aceptación — KAN-93, KAN-94, KAN-95 y KAN-98

Actualización tras integrar development (`eafbf31`): la aceptación fue repetida
sobre la versión combinada. Ver [resultados vigentes](INTEGRACION-DEVELOPMENT-2026-11-09.md)
y sus evidencias. Los recuentos y las mediciones de 14:40 UTC que siguen son
históricos; la carga vigente finalizó a las 22:06 UTC con p95 35,45 ms y cero errores.

Fecha de contraste con Jira: 9 de noviembre de 2026.
Se leyeron las descripciones completas de los cuatro tickets y sus 13 criterios.
Este informe vincula cada criterio con pruebas ejecutadas; no sustituye el
estado del tablero ni modifica Jira.

Resultado final: los 13 criterios tienen evidencia aprobada dentro del alcance
descrito. Pasaron 768 pruebas Java, 24 de interfaz, 142 contratos REST, cuatro
casos HTTP ampliados de idempotencia y siete recorridos completos de sesión y
negocio. `mvn verify` aprobó cobertura y mutación sobre el código corregido;
se añadió después un caso unitario de fallo del segundo evento de lote y se
repitió la suite Java completa. PIT reportó 98% en las clases analizadas, con
un mutante por timeout detallado en su informe.

Carga final del 9/11 a las 14:40 UTC: 50 usuarios, cinco minutos, p95 global
32,00 ms, 0% de errores HTTP y 100% checks. Todos los umbrales por endpoint
aprobaron. Se retiraron las sondas y el único mensaje inválido de prueba.
La prueba de idempotencia escribió usuarios y reservas de QA identificados con
correo `.invalid`; esos datos se conservan para la trazabilidad de la ejecución.

Extractos operativos conservados: [DLQ](evidencia/KAN-93-dlq-invalido.log) y
[XA](evidencia/KAN-94-xa-probe.log).

## Matriz de criterios

| Ticket y criterio de Jira | Evidencia de aceptación |
|---|---|
| KAN-93: mensaje inválido o fallido N veces termina en DLQ | Solicitud de sincronización fallida: entregas 1–5 y DLQ el 8/11. Mensaje de tipo inválido: entregas 1–5 y DLQ el 9/11, correlación `qa-invalid-jms-20261109`. |
| KAN-93: conserva correlación y causa saneada | Correlación preservada en DLQ; `cause=MessageFormatException` en los cinco avisos. El marcador privado del payload no aparece en el log. `FalloJmsTest` verifica ausencia de secretos, causa original y saltos de línea inyectados. |
| KAN-93: procedimiento probado de reproceso | `move-message` individual devolvió `true`; el mock OTA recibió un PUT de inventario y otro de tarifas; conteo posterior del mensaje de prueba en DLQ = 0. |
| KAN-94: falla intermedia produce rollback completo o compensación | Sonda XA real: violación de PK tras publicar, rechazo real del broker tras INSERT y excepción tras ambas escrituras. Solo persistieron la fila y el mensaje del caso exitoso. |
| KAN-94: no quedan datos sin su evento obligatorio | PGXADataSource + JmsXA; prepare real, commit y tres rollbacks. Comprobación final: una fila y un mensaje, ambos del caso exitoso, y cero transacciones preparadas pendientes. |
| KAN-94: pruebas de falla en cada punto crítico | Pruebas de propagación por registro, recuperación/reset, persistencia de pago, cobro, confirmación, publicación simple/lote y overbooking, combinadas con la prueba persistente XA. Ver la matriz de operaciones más abajo. |
| KAN-95: repetir la clave crea un único recurso | Aceptación HTTP concurrente de reserva, pago individual y lote: tres solicitudes simultáneas por operación devuelven el mismo recurso. Los listados de usuarios no muestran recursos duplicados del caso. |
| KAN-95: respuesta repetida equivalente | Comparación del cuerpo JSON completo en los tres POST y de Location en reserva/pago individual, además de repetición secuencial. |
| KAN-95: clave asociada a usuario y operación correctos | Misma clave usada por dos usuarios: recursos diferentes y propiedad correcta; accesos cruzados 403/404. Misma clave en reserva y pago: namespaces separados. Reutilización de clave de pago para lote distinto: 409, reservas sin confirmar. |
| KAN-98: tres endpoints seleccionados y justificados | Hoteles, disponibilidad y tarifas: entrada al catálogo, cupo y precio del recorrido de reserva; guion k6 y justificación en KAN-98. |
| KAN-98: p95 < 500 ms | Resultado por endpoint y global adjunto en el informe de carga. |
| KAN-98: error < 1% | Tasa global y por endpoint en el informe de carga; umbrales ejecutables de k6. |
| KAN-98: reporte adjunto al informe de ejecución | [Resumen adjunto](evidencia/KAN-98-k6-summary.md) y [métricas completas adjuntas](evidencia/KAN-98-k6-summary.json), conservados con este informe. |

Fuentes originales:
[KAN-93](https://agostinabernard.atlassian.net/browse/KAN-93),
[KAN-94](https://agostinabernard.atlassian.net/browse/KAN-94),
[KAN-95](https://agostinabernard.atlassian.net/browse/KAN-95),
[KAN-98](https://agostinabernard.atlassian.net/browse/KAN-98).

## Operaciones que persisten y publican eventos

| Operación | Frontera y prueba |
|---|---|
| Registro de usuario | Persistencia falla: no publica. Publicación falla después de guardar: excepción llega al contenedor. `FallosProductoresObligatoriosTest`. |
| Solicitud de recuperación | Hash/token guardado y publicación fallida: excepción propagada. `FallosProductoresObligatoriosTest`. |
| Reset de contraseña | Cambio y limpieza de token seguidos de aviso fallido: excepción propagada. `FallosProductoresObligatoriosTest`. |
| Pago individual | Persistencia falla antes de cobrar; pasarela falla/rechaza; confirmación falla antes de publicar; publicación falla después de confirmar. `ServicioDePagosImplTest`. |
| Pago por lote | Segundo evento falla después de publicar el primero: se propaga la excepción dentro de la transacción común. `ServicioDePagosImplTest`. |
| Overbooking | Fallo de aviso después de guardar el conflicto: excepción propagada. `FallosProductoresObligatoriosTest`. |
| Sincronización OTA/PMS | Consumidor real falla/reintenta y llega a DLQ; reproceso real y recepción por mocks REST/SOAP. Informe de integración. |

Las pruebas unitarias anteriores verifican orden y propagación, no simulan una
base transaccional. Se complementan con la sonda real `integration/xa-probe`,
que acredita el rollback persistente de JTA/XA en el mismo datasource y broker.
Los cinco publicadores están verificados para usar `java:/JmsXA`.

## Correcciones encontradas al ampliar las pruebas

- El servidor devolvía un pago anterior al reutilizar su clave para otro lote.
  Ahora compara reservas, monto y moneda y responde 409 si no corresponden.
  Un bloqueo pesimista por usuario serializa también solicitudes que apuntan
  a reservas distintas con la misma clave, antes de consultar o cobrar.
- La excepción original JMS podía reaparecer en la cadena que imprime WildFly.
  Ahora se propaga una excepción con diagnóstico saneado, sin adjuntar la causa
  original; el log mantiene tipo, correlación, identificador e intento.

## Alcance y límites de la aceptación

Los criterios de estos tickets no exigen una caída abrupta durante el recovery
XA ni que cada búsqueda de carga use una clave inédita. Esos escenarios no se
confunden con las pruebas ejecutadas: serían pruebas adicionales de resiliencia
y capacidad con caché fría. La corrida contractual usa 50 usuarios, cinco
minutos y consultas repetidas de los tres endpoints indicados.

La pasarela de pagos actual es un mock sin cobro externo persistente. Una
pasarela real necesitaría idempotencia externa y compensación/reconciliación
verificadas, porque HTTP y SMTP no se incorporan a la transacción XA. El
registro idempotente de correo no garantiza exactamente una entrega ante una
caída entre el envío SMTP y su registro en base. Los criterios aquí validados
de consistencia corresponden a PostgreSQL y JMS.

La aceptación corresponde al entorno y casos descritos. No equivale a una
garantía de ausencia de cualquier defecto. El dominio `StayHubSecurity` sigue
siendo exclusivo del WAR; ver [seguridad](../SEGURIDAD_DOMINIO_STAYHUB.md).
