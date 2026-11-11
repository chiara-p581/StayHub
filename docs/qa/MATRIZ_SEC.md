# Informe de seguridad y matriz SEC — KAN-72

Fecha de corte: **10 de noviembre de 2026**

Versión evaluada: `development` @ `e8faeed7e155c8a3103e6361a2b066366a56c5b3`

Ejecución: [StayHub CI #117](https://github.com/chiara-p581/StayHub/actions/runs/42846275834)

## Criterio de evaluación

- **CUMPLE:** existe control implementado y evidencia automatizada o reproducible.
- **NO CUMPLE:** el control requerido no está implementado o una prueba demuestra el fallo.
- **NO VERIFICABLE:** no hay evidencia suficiente para afirmar el resultado.

La matriz distingue el estado comprobado de los riesgos residuales. No se considera evidencia una afirmación generada por IA: cada conclusión remite a código, pruebas, Jira o artefactos de CI.

## Matriz SEC01–SEC13

| Criterio | Amenaza y control validado | Evidencia | Estado | Riesgo residual / decisión |
| --- | --- | --- | --- | --- |
| SEC01 | SOAP del PMS autenticado con certificado cliente y separado de la sesión REST. | [KAN-31](https://agostinabernard.atlassian.net/browse/KAN-31), job `Integración Docker`, `PmsLegacyServiceImplTest`. | **CUMPLE** | La clave y el truststore son secretos de ambiente; nunca se versionan. |
| SEC02 | Identidad tomada de la sesión y precio calculado en servidor para reservas/pagos. | [KAN-32](https://agostinabernard.atlassian.net/browse/KAN-32), JUnit de Reservas/Pagos y recorridos E2E. | **CUMPLE** | Los datos históricos de prueba deben conservar propietario válido o eliminarse de forma consistente. |
| SEC03 | Autorización por objeto en reservas, pagos y funciones administrativas. | [KAN-33](https://agostinabernard.atlassian.net/browse/KAN-33), `live-security-matrix.spec.cjs`, 6 escenarios verdes. | **CUMPLE** | Todo recurso nuevo debe incorporarse a la matriz BOLA. |
| SEC04 | Salidas DOM sanitizadas; DOMPurify se sirve localmente y se bloquean eventos, scripts y URLs `javascript:`. | [KAN-34](https://agostinabernard.atlassian.net/browse/KAN-34), `proyecto/ui-security/seguridad.test.cjs`, job Frontend. | **CUMPLE** | CSP mantiene `unsafe-inline` sólo para estilos; no se habilita en scripts. |
| SEC05 | Tamaño/cantidad de solicitudes, rate limiting, timeouts y límites de recursos; carga nominal validada. | [KAN-35](https://agostinabernard.atlassian.net/browse/KAN-35), `SeguridadHttpFilterTest`, [KAN-98](../tickets/KAN-98-PRUEBA-CARGA.md). | **CUMPLE** | La invalidación distribuida del rate limit no fue probada en múltiples nodos; el despliegue evaluado usa un nodo. |
| SEC06 | HTTPS, HSTS, CSP, cabeceras, cookies `Secure`/`HttpOnly`/`SameSite` y defensa CSRF. | [KAN-36](https://agostinabernard.atlassian.net/browse/KAN-36), `SeguridadHttpFilterTest`, `SeguridadDeclarativaTest`, ZAP. | **CUMPLE** | Respuestas propias de la raíz de WildFly quedan fuera del contexto `/StayHub`; no exponen negocio. |
| SEC07 | Webhooks OTA autenticados por canal con HMAC, timestamp y nonce anti-replay. | [KAN-37](https://agostinabernard.atlassian.net/browse/KAN-37), `AutenticacionWebhookOtaTest`, Newman/E2E. | **CUMPLE** | Los secretos por canal deben cargarse desde configuración del ambiente y rotarse. |
| SEC08 | Egress restringido: HTTPS, allowlist de hosts y bloqueo de loopback/redes privadas para evitar SSRF. | [KAN-38](https://agostinabernard.atlassian.net/browse/KAN-38), `ConfiguracionCanalesTest`, `ConfiguracionCanales`. | **CUMPLE** | Incorporar cada proveedor nuevo a la allowlist y repetir la prueba DNS/URL. |
| SEC09 | Login con límites y bloqueo temporal, recuperación segura, sesión renovada y MFA obligatorio para administradores. | [KAN-39](https://agostinabernard.atlassian.net/browse/KAN-39), `ProteccionLoginTest`, pruebas MFA y E2E de sesión. | **CUMPLE** | La clave maestra MFA es configuración obligatoria de cada ambiente, no un dato compartido por Git. |
| SEC10 | Retorno posterior al login limitado a una allowlist de páginas locales; rechazo de esquemas, barras y codificaciones ambiguas. | [KAN-40](https://agostinabernard.atlassian.net/browse/KAN-40) finalizado y revisión reproducible de `destinoLocalSeguro` en `login-1.js`. | **CUMPLE** | Toda pantalla nueva que necesite retorno debe añadirse explícitamente a la allowlist. |
| SEC11 | Cadena de suministro: versiones fijadas, auditoría OSV/npm, SBOM, escaneo de secretos y librerías frontend locales. | `security-evidence`, `secrets-evidence`, [ESCANEO_SECRETOS.md](../ESCANEO_SECRETOS.md). | **CUMPLE** | Dependabot puede proponer actualizaciones, pero cada PR debe atravesar el pipeline completo. |
| SEC12 | Persistencia y reglas: ownership, transacciones, claves idempotentes, concurrencia optimista y restricciones de datos. | `PersistenciaSeguridadTest`, [KAN-94](https://agostinabernard.atlassian.net/browse/KAN-94), [KAN-95](https://agostinabernard.atlassian.net/browse/KAN-95), [KAN-96](https://agostinabernard.atlassian.net/browse/KAN-96). | **CUMPLE** | La expiración automática de holds fue declarada fuera del alcance funcional actual; KAN-83 ampliará pruebas con infraestructura real. |
| SEC13 | Errores centralizados con Problem Details, correlation ID, auditoría persistente y logs sanitizados. | [KAN-97](https://agostinabernard.atlassian.net/browse/KAN-97), `RespuestaSeguraFilterTest`, `ErrorInesperadoMapperTest`, `AuditoriaSeguridad`. | **CUMPLE** | La retención/rotación del log depende de la configuración operativa de WildFly. |

## Herramientas y resultados independientes

| Control | Resultado comprobado | Evidencia |
| --- | --- | --- |
| OWASP ZAP autenticado | 0 High; 6 grupos Medium/8 instancias revisadas; 0 Medium sin tratar | [Informe KAN-99](../seguridad/INFORME_ZAP_KAN_99.md) y [artefacto](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13778027818) |
| SonarCloud | Quality Gate PASSED; Reliability A; Security A; 0 vulnerabilidades | [SONARCLOUD_QUALITY_GATE.md](../SONARCLOUD_QUALITY_GATE.md) |
| OSV y npm audit | 0 vulnerabilidades reportadas | [security-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13779295721) |
| Gitleaks | 0 secretos en historial y árbol actual | [secrets-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13779411481) |
| SAST/Pruebas | 779 JUnit, 26 frontend y matriz E2E aprobadas | [Informe de ejecución](INFORME_EJECUCION_PRUEBAS.md) |

## Riesgos abiertos que afectan la profundidad de la validación

| Jira | Riesgo | Tratamiento actual |
| --- | --- | --- |
| [KAN-83](https://agostinabernard.atlassian.net/browse/KAN-83) | Falta suite dedicada de persistencia/MDB contra PostgreSQL y Artemis reales. | El pipeline prueba esos componentes mediante el despliegue completo. |
| [KAN-91](https://agostinabernard.atlassian.net/browse/KAN-91) | Falta prueba automatizada de caída y recuperación del broker. | DLQ y reproceso implementados; se valida salud antes de demo. |
| [KAN-92](https://agostinabernard.atlassian.net/browse/KAN-92) | Idempotencia no demostrada para todos los consumidores JMS. | Controles existentes cubren HTTP, OTA y notificación; falta completar la matriz JMS. |

Estos riesgos no cambian a `NO CUMPLE` controles diferentes que sí fueron demostrados; impiden afirmar una cobertura total de resiliencia e idempotencia JMS. Su detalle operativo está en [REGISTRO_DEFECTOS.md](REGISTRO_DEFECTOS.md).

## Conclusión

Los trece criterios SEC poseen control implementado y evidencia trazable en la versión evaluada. No se detectaron vulnerabilidades High ni secretos y el Quality Gate aprobó. La aceptación queda condicionada a mantener secretos fuera del repositorio, ejecutar la CI después de cada merge y conservar visibles las tres brechas abiertas de integración/mensajería.
