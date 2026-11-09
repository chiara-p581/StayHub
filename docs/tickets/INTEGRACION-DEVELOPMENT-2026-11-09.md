# Integración con development — 9 de noviembre de 2026

Base anterior: `7835152`. Nueva base: `eafbf31`, ocho commits más recientes
de development. Tanto development local como feature/seguridad-a incorporan
la nueva base mediante fast-forward, sin reescribir historia.

El trabajo local se resguardó antes de cambiar de rama. Se aplicó el resguardo
sobre la nueva base, manteniéndolo disponible. La integración automática produjo
un único conflicto, en `api.js`: se conservaron `skipReauth` de development y
los encabezados `extraHeaders` de idempotencia. Se agregó además la propagación
de esos encabezados al reintento posterior a una reautenticación, con prueba
de interfaz específica.

## Conservación de los cambios de development

De los 46 archivos modificados por los ocho commits, 40 quedan exactamente
iguales a development. Las seis coincidencias se revisaron:

| Archivo | Integración |
|---|---|
| Workflow de seguridad | Se conserva el flujo MFA y aislamiento de suites; se añade el job de carga propio. |
| OpenAPI | Se conservan las nuevas operaciones y se regeneran los contratos con Idempotency-Key. |
| Esquema Docker | Se conservan tablas MFA, límites y auditoría; se agrega la clave única de reservas. |
| E2E críticos | Se conserva TOTP y aislamiento de cuotas; se agregan fechas con cupo y repetición idempotente. |
| SeguridadHttpFilter | La rama de operaciones sensibles conserva el limitador persistente original; solo los tres GET críticos usan un adaptador propio de cuota. |
| api.js | Se conservan MFA, reautenticación y recuperación; se añaden encabezados de idempotencia también al reintentar. |

No se modificaron MfaService, CifradoMfa, ProteccionLogin, LimitadorSolicitudes,
AuditoriaPersistente, UsuarioResource ni las pantallas de MFA de development.

Durante la verificación en WildFly se detectaron dos invocaciones del filtro
HTTP por solicitud: el descriptor lo registraba como `SeguridadHttpFilter`,
mientras la anotación usaba el nombre predeterminado de la clase completa.
Se hizo coincidir `filterName` con el descriptor existente. Se conserva el
descriptor y todas las comprobaciones; la cuota y la auditoría se ejecutan
una vez por solicitud. Una prueba de regresión verifica la coincidencia de
ambos registros. Antes del ajuste, las suites simultáneas agotaron la cuota
de altas y la carga registró rechazos públicos; esas ejecuciones fallidas no
constituyen evidencia de aceptación.

## Adaptación del ticket de carga a cuotas persistentes

El diagnóstico inicial, preservando el contador persistente por petición,
registró p95 de varios segundos contra Aiven. Para mantener ese trabajo y
cumplir el ticket, se agregó un adaptador en infraestructura, exclusivo de
`GET /hoteles`, `GET /inventario-tarifas/disponibilidad` y
`GET /inventario-tarifas/tarifas`.

El adaptador reserva bloques de 512 permisos con UPSERT en la misma tabla
`limite_solicitud`, antes de concederlos localmente. La cuota sigue compartida
entre nodos y es de 6.000 solicitudes/minuto/IP. El último bloque se recorta
al saldo disponible. Un reinicio pierde los permisos locales sin devolverlos
al saldo persistente: reduce disponibilidad, no aumenta la cuota. Si la base
falla no se conceden permisos nuevos sin respaldo persistente. La reposición
comienza cuando quedan 256 permisos y consulta la base fuera del monitor:
otras solicitudes pueden consumir el saldo ya reservado mientras se repone.
Las operaciones sensibles mantienen sus cuotas
y su implementación de development.

`CuotasLecturasPublicasTest` cubre dos nodos, saldo parcial, reinicio y fallo
de persistencia y consumo concurrente durante una reposición lenta.
Las pruebas de integración de idempotencia amplían únicamente
el tiempo de preparación para admitir las comprobaciones persistentes de
autenticación; sus aserciones funcionales se mantienen.

## Configuración local de MFA

La base tiene las tablas/columnas de MFA, cuotas y auditoría. Se recibió el
XML de configuración por un canal privado y se validó que su clave AES de
256 bits descifra correctamente los dos secretos MFA existentes. Se configuró
`stayhub.mfa.master-key` exclusivamente en el WildFly local, con respaldo
privado del XML anterior. No se reinició MFA ni se modificaron los secretos
de ninguna cuenta. La clave y los respaldos no forman parte del repositorio.

Las pruebas y evidencias anteriores a esta integración conservan su fecha y
base de ejecución; no se presentan como mediciones de la nueva combinación.

## Resultados sobre development integrado

| Verificación | Resultado |
|---|---|
| Java, incluyendo seguridad, MFA y regresión del filtro | 780 pruebas aprobadas, cero fallos. |
| Maven verify final | Aprobado con JaCoCo y PIT: cobertura de líneas de clases mutadas 1.390/1.420 (98%), 981/1.004 mutaciones detectadas (98%); un mutante terminó por timeout, registrado por PIT. |
| Interfaz | 25 pruebas aprobadas, incluida conservación de Idempotency-Key tras reautenticación. |
| Contratos | 143/143 pedidos, 61 operaciones y 75 esquemas validados. |
| Accesibilidad y diseño adaptable | 209 pruebas aprobadas. |
| HTTP real | 11 pruebas aprobadas: sesiones, carrito/compra/pago, cancelación, OTA firmada y cuatro casos ampliados de idempotencia. |
| ADMIN con MFA | Alta y autenticación realizadas por la usuaria; panel operativo, Booking y PMS recibidos por mocks locales. |
| Overbooking | Conflicto ficticio 5 resuelto con SUITE, hold persistido y notificación publicada/consumida. |
| XA | Commit conjunto, fallo real de base, rechazo real del broker y excepción de aplicación aprobados; estado final de una fila y un mensaje. Cero transacciones preparadas pendientes. |
| DLQ | Cinco intentos para mensaje inválido y solicitud válida recuperable; correlación conservada, contenido del inválido ausente del log y reproceso válido recibido por el mock. |
| Carga final, 22:06 UTC | 50 usuarios, cinco minutos, 7.266 iteraciones; p95 global 35,45 ms, cero errores HTTP y 100% checks. Todos los umbrales por endpoint aprobados. |

Evidencia actual: [integración y recorridos](evidencia/INTEGRACION-DEVELOPMENT-2026-11-09.md),
[informe de carga](evidencia/KAN-98-k6-development-summary.md) y
[métricas completas](evidencia/KAN-98-k6-development-summary.json).

Se retiraron las sondas, sus colas exclusivas, los dos mocks y las cuatro
propiedades temporales de integración. StayHub permanece desplegado en
WildFly local; el dominio compartido `other` conserva `integrated-jaspi=true`.
El despliegue respondió `OK`, el datasource XA validó su conexión y
`StayHubSecurity` referencia únicamente `StayHub.war`. Se compararon los 366
archivos funcionales del WAR final con el desplegado: cero diferencias.
Los usuarios, reservas y el conflicto ficticio de QA se conservan como evidencia.
La prueba no simula cortes abruptos durante recovery XA ni una pasarela de
cobro externa real. No se realizó commit ni push durante esta verificación.
