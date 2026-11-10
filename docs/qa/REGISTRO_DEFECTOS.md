# Registro de defectos — KAN-71

Fecha de corte: **10 de noviembre de 2026**

Ejecución de referencia: [StayHub CI #117](https://github.com/chiara-p581/StayHub/actions/runs/42846275834)

Responsable QA del corte: **Chiara Prevosti**

## Convenciones

- Severidad: impacto técnico o de negocio (`Crítica`, `Alta`, `Media`, `Baja`).
- Prioridad: orden de atención en el proyecto (`Alta`, `Media`, `Baja`).
- Estado: `Abierto`, `Corregido pendiente de merge`, `Cerrado` o `Aceptado`.
- La ejecución #117 no tuvo fallos ni bloqueos. Este registro incluye además brechas conocidas que todavía necesitan una prueba específica.
- Jira no posee actualmente incidencias de tipo `Bug` para estas brechas: se administran como tareas técnicas vinculadas. La trazabilidad se conserva por clave y enlace.

## Pendientes conocidos

| ID | Defecto o brecha | Sev. / prioridad | Reproducción y resultado actual | Resultado esperado | Evidencia / workaround | Estado |
| --- | --- | --- | --- | --- | --- | --- |
| [KAN-83](https://agostinabernard.atlassian.net/browse/KAN-83) | Falta una suite dedicada de repositorios/MDB contra PostgreSQL y Artemis reales. | Media / Media | La CI prueba la aplicación desplegada contra ambos servicios, pero no ofrece un harness aislado por repositorio y consumidor. | Poder ejecutar casos específicos de persistencia y mensajería con infraestructura real y datos controlados. | `Integración Docker` cubre el flujo completo mientras se implementa la suite dedicada. | Abierto |
| [KAN-91](https://agostinabernard.atlassian.net/browse/KAN-91) | No está automatizada la caída y recuperación del broker durante un flujo activo. | Alta / Media | Detener/reiniciar Artemis a mitad de una publicación no forma parte de la CI actual. | Probar reentrega, recuperación y ausencia de pérdida tras reinicio. | DLQ, reintentos y reproceso ya existen por KAN-93; antes de una demo se valida salud del broker y DLQ vacía. | Abierto |
| [KAN-92](https://agostinabernard.atlassian.net/browse/KAN-92) | La idempotencia no está demostrada para todos los consumidores JMS. | Alta / Media | Reenviar el mismo mensaje a cada consumidor no está cubierto por una matriz completa. | Un duplicado no debe repetir el efecto de negocio. | Idempotencia HTTP, protección de replay OTA e idempotencia de email reducen el riesgo; se monitorean `JMSCorrelationID` y claves de negocio. | Abierto |

No hay defectos abiertos de severidad crítica. KAN-91 y KAN-92 son los riesgos técnicos más importantes porque una reentrega JMS puede ocurrir aunque el flujo nominal sea correcto.

## Defecto menor corregido en esta rama

| Campo | Detalle |
| --- | --- |
| ID local | QA-LOAD-001 |
| Título | k6 ejecutaba la carga pero no podía escribir los resúmenes en el volumen montado |
| Severidad / prioridad | Baja / Media |
| Pasos | Ejecutar el trabajo `Carga k6`; observar `permission denied` al crear `performance/reports/k6-summary.md` y `.json`. |
| Esperado | Conservar métricas y resumen como artefacto descargable. |
| Actual previo | La prueba y sus umbrales terminaban correctamente, pero sólo quedaba el log de Compose. |
| Causa raíz | La imagen oficial usa un usuario no-root y el directorio del host no era escribible desde el contenedor. |
| Corrección | Limitar el permiso de escritura a `performance/reports` antes de ejecutar k6, tanto en CI como en el lanzador local. |
| Estado | Corregido pendiente de merge y de la próxima CI. |

## Defectos y riesgos cerrados

En las filas siguientes, “repetir” describe la reproducción original, “esperado” el criterio de corrección y “obtenido” la evidencia posterior. Todos tenían prioridad Jira `Medium` al cierre.

| Jira | Sev./pri. | Reproducción; esperado; obtenido | Evidencia, estado y causa raíz |
| --- | --- | --- | --- |
| [KAN-31](https://agostinabernard.atlassian.net/browse/KAN-31) | Alta/Media | Repetir: invocar SOAP sin identidad técnica. Esperado: rechazo. Obtenido: sólo el cliente con certificado PMS accede. | `Integración Docker`; **Cerrado**. Causa: la frontera SOAP no estaba cubierta por el filtro REST. |
| [KAN-32](https://agostinabernard.atlassian.net/browse/KAN-32) | Alta/Media | Repetir: alterar email/precio del navegador. Esperado: ignorarlos. Obtenido: identidad de sesión y precio servidor. | JUnit/E2E; **Cerrado**. Causa: datos de autoridad aceptados desde el cliente. |
| [KAN-33](https://agostinabernard.atlassian.net/browse/KAN-33) | Alta/Media | Repetir: huésped consulta recurso ajeno. Esperado: 403/404. Obtenido: acceso denegado. | Matriz E2E; **Cerrado**. Causa: faltaban controles de propietario por objeto. |
| [KAN-34](https://agostinabernard.atlassian.net/browse/KAN-34) | Alta/Media | Repetir: renderizar payload con evento/script. Esperado: texto seguro. Obtenido: nodos/atributos peligrosos eliminados. | `seguridad.test.cjs`; **Cerrado**. Causa: escrituras DOM con HTML no confiable. |
| [KAN-35](https://agostinabernard.atlassian.net/browse/KAN-35) | Alta/Media | Repetir: exceso de solicitudes/tamaño. Esperado: límite y 429/413. Obtenido: controles activos; carga nominal aprobada. | JUnit y k6; **Cerrado**. Causa: límites transversales incompletos. |
| [KAN-36](https://agostinabernard.atlassian.net/browse/KAN-36) | Alta/Media | Repetir: petición mutadora sin CSRF o revisar HTTP/cookies. Esperado: rechazo y flags/cabeceras seguras. Obtenido: controles presentes. | JUnit/ZAP; **Cerrado**. Causa: configuración web parcialmente endurecida. |
| [KAN-37](https://agostinabernard.atlassian.net/browse/KAN-37) | Alta/Media | Repetir: webhook sin firma, vencido o con nonce repetido. Esperado: rechazo. Obtenido: HMAC por canal, timestamp y nonce validados. | `AutenticacionWebhookOtaTest`; **Cerrado**. Causa: secreto compartido sin identidad ni frescura. |
| [KAN-38](https://agostinabernard.atlassian.net/browse/KAN-38) | Alta/Media | Repetir: configurar loopback/red privada/HTTP. Esperado: rechazo. Obtenido: sólo HTTPS y destinos permitidos. | `ConfiguracionCanalesTest`; **Cerrado**. Causa: destinos externos configurables sin validación. |
| [KAN-39](https://agostinabernard.atlassian.net/browse/KAN-39) | Alta/Media | Repetir: intentos masivos o recuperación inválida. Esperado: limitación y token de un uso. Obtenido: bloqueo, recuperación segura y MFA admin. | `ProteccionLoginTest` y E2E; **Cerrado**. Causa: autenticación sin controles de abuso completos. |
| [KAN-40](https://agostinabernard.atlassian.net/browse/KAN-40) | Alta/Media | Repetir: `next` externo, absoluto o codificado. Esperado: ignorarlo. Obtenido: sólo páginas locales de allowlist. | `destinoLocalSeguro`; **Cerrado**. Causa: retorno usado sin validación estricta. |
| [KAN-65](https://agostinabernard.atlassian.net/browse/KAN-65) | Media/Media | Repetir: suite completa de Canales Externos. Esperado: finalizar. Obtenido: backend 779/779 sin bloqueo. | CI #117; **Cerrado**. Causa: recurso de prueba no liberado. |
| [KAN-90](https://agostinabernard.atlassian.net/browse/KAN-90) | Alta/Media | Repetir: dependencia lenta/caída. Esperado: timeout y respuesta controlada. Obtenido: fallback sin espera indefinida. | JUnit de integraciones; **Cerrado**. Causa: llamadas remotas sin política uniforme. |
| [KAN-93](https://agostinabernard.atlassian.net/browse/KAN-93) | Alta/Media | Repetir: forzar fallo permanente de consumidor. Esperado: DLQ y reproceso trazable. Obtenido: mensaje y metadatos recuperables. | Pruebas JMS/procedimiento; **Cerrado**. Causa: circuito de error no operacionalizado. |
| [KAN-94](https://agostinabernard.atlassian.net/browse/KAN-94) | Alta/Media | Repetir: error entre persistencia y publicación. Esperado: rollback consistente. Obtenido: transacción administrada revierte. | JUnit transaccional; **Cerrado**. Causa: límites transaccionales no explicitados. |
| [KAN-95](https://agostinabernard.atlassian.net/browse/KAN-95) | Alta/Media | Repetir: mismo POST con igual clave. Esperado: un solo efecto. Obtenido: respuesta idempotente; clave ausente/inválida rechazada. | E2E/`ClaveIdempotencia`; **Cerrado**. Causa: operaciones sin clave estable. |
| [KAN-96](https://agostinabernard.atlassian.net/browse/KAN-96) | Alta/Media | Repetir: actualizar con ETag vencido. Esperado: conflicto. Obtenido: `If-Match` evita sobreescritura. | Pruebas de concurrencia; **Cerrado**. Causa: falta de versión optimista HTTP. |
| [KAN-97](https://agostinabernard.atlassian.net/browse/KAN-97) | Media/Media | Repetir: provocar 400/401/403/404/409/429/500. Esperado: formato estable sin detalle interno. Obtenido: Problem Details + correlation ID. | `RespuestaSeguraFilterTest`; **Cerrado**. Causa: mapeadores/formats dispersos. |
| [KAN-99](https://agostinabernard.atlassian.net/browse/KAN-99) | Alta/Media | Repetir: ZAP autenticado en ambos roles. Esperado: 0 High y todo Medium tratado. Obtenido: 0 High, 0 Medium sin revisar. | [Informe ZAP](../seguridad/INFORME_ZAP_KAN_99.md); **Cerrado**. Causa: faltaban gate y clasificación documentada. |

## Criterio de actualización

Este registro se actualiza cuando una prueba descubre un comportamiento distinto del esperado, cuando una brecha cambia de alcance o cuando una corrección se integra en `development`. Un ticket sólo pasa a cerrado cuando existe evidencia reproducible; una ejecución verde no borra brechas que todavía no fueron ejercitadas.
