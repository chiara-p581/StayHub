# Tickets y evidencia de entrega

**Verificación vigente tras integrar development (`eafbf31`):**
[informe del 9/11](INTEGRACION-DEVELOPMENT-2026-11-09.md).
780 pruebas Java, 25 de interfaz, 209 de accesibilidad, 143 contratos y once
pruebas HTTP aprobadas. Carga: 50 usuarios durante cinco minutos, p95 35,45 ms,
cero errores. Incluye repetición de DLQ/reproceso, XA y recorridos ADMIN con MFA.
Los resultados descritos a continuación corresponden a ejecuciones históricas
anteriores a esta integración.

[Matriz de aceptación de los 13 criterios de Jira](ACEPTACION-CUATRO-TICKETS.md).
La aceptación ampliada final aprobó los 13 criterios: incluye concurrencia y
aislamiento de claves, saneamiento de fallos JMS y carga final con p95 32,00 ms,
0% errores y métricas completas adjuntas al informe.

- [KAN-93 — DLQ y reproceso](KAN-93-DLQ-REPROCESO.md)
- [KAN-94 — Consistencia XA](KAN-94-CONSISTENCIA-XA.md)
- [KAN-95 — Idempotencia](KAN-95-IDEMPOTENCIA.md)
- [KAN-98 — Prueba de carga](KAN-98-PRUEBA-CARGA.md)
- [Dominio de seguridad exclusivo de StayHub](../SEGURIDAD_DOMINIO_STAYHUB.md)
- [Pruebas reales de integración y bloqueo XA](VERIFICACION-INTEGRACION-2026-11-08.md)

Regresión ejecutada el 8 de noviembre de 2026: 756 pruebas Java, 24 pruebas
de interfaz, 209 pruebas de accesibilidad, contratos REST 142/142 y cinco
operaciones del WSDL desplegado. Los documentos individuales distinguen las
pruebas reales de los requisitos que todavía necesitan evidencia de integración.

La verificación Maven `verify` del WAR final terminó correctamente, incluidos
JaCoCo y PIT. PIT reportó 98% de cobertura de líneas en las clases mutadas y
98% de mutaciones detectadas (incluye un timeout; el detalle está en su informe).
Sobre el despliegue final pasaron las cuatro pruebas de sesión y los tres
recorridos de compra/pago, cancelación y OTA. La matriz de abuso pasó sus seis
casos en una ejecución separada anterior al último redespliegue.
La consulta de administración confirmó `referencing-deployments=[StayHub.war]`
para `StayHubSecurity`, `other.integrated-jaspi=true` y despliegue `OK`.

Actualización 9/11/2026: resuelto el bloqueo XA. Se habilitaron transacciones
preparadas en Aiven, se migró el datasource local a PGXADataSource y pasaron
los cuatro escenarios de commit/rollback distribuido y las siete pruebas de
sesión y negocio con XA activo. El detalle y el instrumental reproducible se
encuentran en [KAN-94](KAN-94-CONSISTENCIA-XA.md).
La repetición de carga con XA activo aprobó: 50 usuarios durante cinco minutos,
p95 global 36,88 ms, 0% de errores HTTP y 100% de checks.

Las pruebas que registran usuarios y crean reservas escriben datos de prueba
en la base conectada al WildFly local. La prueba de fuerza bruta debe ejecutarse
al final: activa tanto la cuota por minuto como la protección de login de
15 minutos y puede impedir temporalmente los logins de otras suites en la misma IP.
