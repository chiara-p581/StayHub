# KAN-98 — Carga sobre tres endpoints críticos

Resultado vigente después de integrar development, 9/11 a las 22:06 UTC:
50 usuarios durante cinco minutos, p95 35,45 ms, cero errores y 100% checks.
Ver [informe actual](evidencia/KAN-98-k6-development-summary.md) y
[métricas completas](evidencia/KAN-98-k6-development-summary.json).
Las mediciones que siguen se conservan como historial previo a la integración.

Se ejecutó k6 contra WildFly local y PostgreSQL remoto con 50 usuarios
concurrentes durante 5 minutos. Cada usuario consulta hoteles, disponibilidad
y tarifas y espera 2 segundos entre recorridos. Se exige p95 < 500 ms,
errores HTTP < 1% y checks > 99%, además de umbrales por endpoint.

Resultado del WAR final con datasource XA y correcciones de aceptación,
9 de noviembre de 2026, 14:40 UTC:

| Endpoint | p95 | Error HTTP |
|---|---:|---:|
| Hoteles | 34,39 ms | 0% |
| Disponibilidad | 26,24 ms | 0% |
| Tarifas | 29,21 ms | 0% |

Global: p95 32,00 ms, 0% errores y 100% checks. Resultado aprobado.
Las métricas completas de esa
ejecución están en `performance/reports/k6-summary.json` y el resumen Markdown
en la misma carpeta; son archivos de evidencia ignorados por Git.
El informe definitivo conserva además copias versionables adjuntas:
[resumen](evidencia/KAN-98-k6-summary.md) y
[métricas completas](evidencia/KAN-98-k6-summary.json).

La primera carga expuso agotamiento de conexiones en Aiven. Se incorporó
caché de consultas públicas de 10 segundos, con tamaño y cargas simultáneas
acotados, y se separaron los contadores públicos de los sensibles. Las lecturas
públicas admiten 6.000 peticiones/minuto/IP; las operaciones sensibles conservan
sus límites. Reservas y pagos siguen validándose en servidor contra la base.
El escenario mide consultas repetidas sobre un mismo hotel y rango: no prueba
la misma capacidad con todas las claves distintas ni mide carga de escrituras.

La medición final incluye invalidación de inventario al mutar reservas para que
la siguiente lectura refleje el cupo actualizado. Pasó también la regresión real
de creación, pago, cancelación y OTA.
La invalidación se publica después del commit. La caché es local a cada WAR:
en un despliegue con varios nodos, los otros nodos dependen del TTL y necesitarían
invalidación distribuida para ofrecer la misma actualización inmediata.
La repetición del 9/11 utiliza PGXADataSource y un pool máximo de 8 conexiones.
También aprobó tras la migración, manteniendo los mismos umbrales.

La seguridad declarativa utiliza exclusivamente `StayHubSecurity` para el WAR;
`other` conserva su configuración. Ver
[dominio y límites de la evidencia](../SEGURIDAD_DOMINIO_STAYHUB.md).
El guion y la ejecución reproducible están en `performance/README.md`.
