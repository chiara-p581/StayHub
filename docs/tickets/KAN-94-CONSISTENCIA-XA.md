# KAN-94 — Consistencia entre base y JMS

Revalidado después de integrar development: los cuatro escenarios XA aprobaron,
con cero transacciones preparadas pendientes. Ver [evidencia del 9/11](evidencia/INTEGRACION-DEVELOPMENT-2026-11-09.md).

Los publicadores usan `java:/JmsXA` para incorporarse a la transacción JTA del
servicio. La alternativa Docker configura PostgreSQL con transacciones
preparadas y un datasource XA `PGXADataSource`.

Los cambios están en los publicadores, `docker/wildfly/configure.cli` y
`compose.yaml`. La regresión Java verifica los contratos y comportamientos
implementados; no sustituye la prueba distribuida de rollback.

Limitación inicial detectada el 8/11: el WildFly local usaba `StayHubDS`, JNDI
`java:/PostgresDS`, como datasource JDBC con `jta=true`, no como datasource XA.
En ese momento no estaba validada la atomicidad de dos recursos XA. El bloqueo
quedó resuelto el 9/11 según la evidencia siguiente.

Comprobación directa del 8 de noviembre de 2026: Aiven informó
`max_prepared_transactions=0`, `max_connections=20`, cero transacciones
preparadas pendientes y usuario sin privilegio superusuario. Una transacción
de prueba con `SELECT 1` seguida de `PREPARE TRANSACTION` falló con SQLState
`55000`, indicando que las transacciones preparadas están deshabilitadas.
Se ejecutó rollback y no se cambiaron datos de negocio. Esa capacidad debía
habilitarse en el servicio Aiven antes de migrar el datasource local a XA.
La [referencia oficial de Aiven](https://aiven.io/docs/products/postgresql/reference/advanced-params)
expone `pg.max_prepared_transactions` e indica que cambiarlo reinicia el servicio.
El cambio requirió acceso administrativo al servicio y una ventana autorizada
para ese reinicio; las credenciales JDBC de StayHub no proporcionan ese acceso.

## Resolución y prueba real del 9 de noviembre de 2026

La administradora habilitó `max_prepared_transactions=20` y Aiven se reinició.
Se comprobó PREPARE seguido de ROLLBACK PREPARED sin cambios de negocio.
`config/migrar-datasource-xa-local.py` respaldó la configuración privada y
migró StayHubDS a `org.postgresql.xa.PGXADataSource`, conservando JNDI, URL TLS
y credenciales. El pool local quedó limitado a 8 conexiones sobre un servidor
que admite 20. WildFly recargó y el WAR quedó en estado `OK`.

La sonda temporal `integration/xa-probe` verificó:

| Caso | Resultado verificado |
|---|---|
| Commit de INSERT y JMS | Una fila y un mensaje persistentes |
| Violación real de PK después de publicar JMS | El mensaje se revirtió |
| Rechazo real de Artemis por dirección de prueba llena después del INSERT | La fila se revirtió |
| Excepción de aplicación después de ambas escrituras | Ambos efectos se revirtieron |

El estado final conservó exclusivamente la fila y el mensaje del caso exitoso.
Las métricas del datasource registraron `XAPrepareCount=1`, `XACommitCount=1`
y `XARollbackCount=3` antes de ejecutar la regresión funcional. Esto acredita
enlistamiento real y prepare de dos fases, además de las aserciones de negocio.
Pasaron las siete pruebas reales de sesión, compra/pago, cancelación y OTA
después de migrar. Se eliminó la tabla de prueba, se drenaron y retiraron sus
dos colas y se retiró el WAR de la sonda. El instrumental fuente se conserva
para reproducir la verificación; no forma parte del WAR de StayHub.
La comprobación posterior de `pg_prepared_xacts` devolvió cero transacciones
preparadas pendientes.
La carga posterior de 50 usuarios durante cinco minutos también aprobó con
p95 global 36,88 ms, 0% de errores y 100% de checks; ver [KAN-98](KAN-98-PRUEBA-CARGA.md).

Alcance: se validaron commit y rollback con los fallos indicados. No se simuló
un corte de energía durante la ventana entre prepare y commit; esa prueba de
recuperación ante caída abrupta requiere un escenario adicional.

Seguridad: se adoptó un dominio exclusivo para el WAR, sin relajar `other`.
Ver [StayHubSecurity](../SEGURIDAD_DOMINIO_STAYHUB.md). Esa decisión resuelve
la propagación de identidad; no reemplaza los requisitos transaccionales XA.
