# KAN-93 — DLQ y reproceso de mensajes fallidos

Revalidado después de integrar development: cinco entregas, DLQ, correlación,
saneamiento y reproceso válido aprobados. Ver [evidencia del 9/11](evidencia/INTEGRACION-DEVELOPMENT-2026-11-09.md).

Se configuraron reintentos de entrega y `StayHubDLQ` en los scripts de WildFly.
Los consumidores propagan los fallos para permitir rollback/reentrega y
registran identificadores JMS, correlación e intento sin incluir el cuerpo.
El procedimiento de reproceso individual está en
[DLQ, transacciones e idempotencia](../DLQ_TRANSACCIONES_E_IDEMPOTENCIA.md).

Los cambios principales están en `config/notificaciones-reintentos.cli`,
`config/seguridad-a-jms.cli`, `docker/wildfly/configure.cli` y los consumidores
de notificaciones y sincronización. Las pruebas Java cubren la propagación
de fallos y los contratos de configuración.

Prueba en vivo aprobada el 8 de noviembre de 2026: una sonda temporal sin
endpoints HTTP publicó una solicitud de sincronización con correlación
`stayhub-dlq-probe-20261108`. Al faltar configuración OTA se observaron entregas
1 a 5, separadas por aproximadamente 10 segundos, y traslado a `StayHubDLQ`.
El mensaje conservó el origen y la correlación. Tras configurar el mock en
127.0.0.1, `move-message` con el JMSMessageID devolvió `true`, y el mock recibió
un PUT de inventario y uno de tarifas. Se verificó el estado de las colas.

También se publicó una solicitud PMS por la cola real y el mock recibió por
SOAP dos registros de disponibilidad y dos tarifas del hotel 2. Desde la
sesión ADMIN de la interfaz se aceptaron ambas sincronizaciones en cola.

Aceptación ampliada del 9/11: un mensaje de tipo inválido con correlación
`qa-invalid-jms-20261109` produjo cinco avisos con `MessageFormatException`
y terminó en DLQ. El payload centinela no apareció en el log. Se retiraron
solamente ese mensaje de prueba y la sonda temporal. `FalloJmsTest` verifica
también que no se adjunte al error propagado una causa con datos sensibles.

Seguridad: el puente de identidad queda limitado al dominio exclusivo
`StayHubSecurity`; `other` conserva su configuración. Ver
[decisión y validación del dominio](../SEGURIDAD_DOMINIO_STAYHUB.md).
El reproceso requiere acceso administrativo y debe hacerse por mensaje.
