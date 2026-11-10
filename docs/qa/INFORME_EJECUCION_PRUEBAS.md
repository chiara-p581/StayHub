# Informe de ejecución de pruebas — KAN-70

Fecha de corte: **10 de noviembre de 2026**

Rama evaluada: `development`

Commit: `e8faeed7e155c8a3103e6361a2b066366a56c5b3`

Ejecución: [StayHub CI #117](https://github.com/chiara-p581/StayHub/actions/runs/42846275834)

Resultado general: **APROBADO** (todos los trabajos finalizaron correctamente).

## Resumen ejecutivo

| Nivel o control | Ejecutadas | Aprobadas | Fallidas | Bloqueadas | Resultado |
| --- | ---: | ---: | ---: | ---: | --- |
| Backend Java (JUnit) | 779 | 779 | 0 | 0 | Aprobado |
| Frontend (Node) | 26 | 26 | 0 | 0 | Aprobado |
| Integración REST, SOAP, PostgreSQL y JMS | 49 | 49 | 0 | 0 | Aprobado |
| Recorridos E2E críticos y matriz de seguridad | 15 | 15 | 0 | 0 | Aprobado |
| Responsive y accesibilidad | 209 | 209 | 0 | 0 | Aprobado |
| Carga k6: 50 usuarios durante 5 minutos | 1 | 1 | 0 | 0 | Aprobado |
| ZAP autenticado: Huésped y Administrador | 2 | 2 | 0 | 0 | Aprobado |

No hubo fallos ni bloqueos en la ejecución de referencia. Las brechas conocidas que no forman parte de un fallo de esta corrida están registradas en [REGISTRO_DEFECTOS.md](REGISTRO_DEFECTOS.md).

## Calidad del código y cobertura

| Indicador | Resultado | Umbral | Estado |
| --- | ---: | ---: | --- |
| Líneas de negocio cubiertas por JaCoCo | 98,3% (1406/1431) | 80% | Aprobado |
| Ramas de negocio cubiertas por JaCoCo | 90,5% (730/807) | 70% | Aprobado |
| Mutation score PIT | 97,7% (981/1004 mutaciones eliminadas) | 60% | Aprobado |
| SonarCloud Quality Gate | PASSED | PASSED | Aprobado |
| Vulnerabilidades de dependencias OSV | 0 | 0 críticas/altas abiertas | Aprobado |
| Auditorías npm | 0 vulnerabilidades | 0 críticas/altas abiertas | Aprobado |
| Secretos detectados por Gitleaks | 0 | 0 | Aprobado |

El alcance obligatorio de JaCoCo corresponde a las capas de negocio `service` y `model`, más el carrito de Reservas. La cobertura global del repositorio es informativa y no sustituye esta medición. El detalle de alcance y exclusiones está en [COBERTURA_JACOCO.md](../COBERTURA_JACOCO.md) y [MUTATION_TESTING_PIT.md](../MUTATION_TESTING_PIT.md).

## Rendimiento

La prueba reproducible de [KAN-98](../tickets/KAN-98-PRUEBA-CARGA.md) sostuvo 50 usuarios virtuales durante 5 minutos y completó 7450 iteraciones.

| Operación | p95 | Tasa de error |
| --- | ---: | ---: |
| Catálogo de hoteles | 15,56 ms | 0,000% |
| Disponibilidad | 11,53 ms | 0,054% |
| Tarifas | 8,84 ms | 0,094% |
| Global | 12,39 ms | 0,049% |

Los umbrales eran p95 menor a 500 ms y errores menores a 1%. Los checks finalizaron con 99,951% de éxito.

Durante la revisión se detectó que k6 podía ejecutar correctamente pero no escribir `k6-summary.md` ni `k6-summary.json` en el volumen del host. La causa era el usuario no privilegiado del contenedor. Esta rama concede permiso únicamente al directorio descartable `performance/reports`; la próxima ejecución conservará los resúmenes además del log. Los resultados de la ejecución de referencia siguen disponibles en el log inmutable de GitHub Actions.

## Seguridad dinámica

- ZAP autenticado recorrió los roles Huésped y Administrador con MFA.
- Alertas High: **0**.
- Grupos Medium: **6**, con 8 instancias únicas, todas revisadas y tratadas o justificadas.
- Hallazgos Medium sin revisar: **0**.
- Informe técnico: [INFORME_ZAP_KAN_99.md](../seguridad/INFORME_ZAP_KAN_99.md).
- Matriz completa SEC01–SEC13: [MATRIZ_SEC.md](MATRIZ_SEC.md).

## Artefactos verificables

Los artefactos se conservan 14 días; el código, las configuraciones y los comandos de reproducción permanecen versionados.

| Evidencia | Artefacto de la ejecución #117 |
| --- | --- |
| Backend, JaCoCo, PIT y Sonar | [backend-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13777742555) |
| Frontend | [frontend-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13779335291) |
| Contratos REST y colecciones | [api-contracts](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13777656985) |
| Integración Docker REST/SOAP/mocks | [integration-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13778062469) |
| Mock SOAP PMS | [pms-mock](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13778222052) |
| E2E | [e2e-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13779505709) |
| Accesibilidad y Lighthouse | [ui-accesibilidad-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13778309125) |
| OWASP ZAP autenticado | [zap-authenticated-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13778027818) |
| SCA, SBOM y auditorías npm | [security-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13779295721) |
| Escaneo de secretos | [secrets-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13779411481) |
| Carga k6 | [load-test-evidence](https://github.com/chiara-p581/StayHub/actions/runs/42846275834/artifacts/13778937454) |

## Reproducción

Desde la raíz del repositorio:

```powershell
mvn -f proyecto/pom.xml clean verify
cd proyecto/ui-security
npm ci
npm test
```

La integración completa y los recorridos desplegados se reproducen con el workflow `StayHub CI`. La carga local se ejecuta según [performance/README.md](../../performance/README.md).

## Conclusión

La versión evaluada cumple los umbrales definidos y no presenta fallos o bloqueos en la corrida de referencia. Los tres riesgos de prueba todavía abiertos —KAN-83, KAN-91 y KAN-92— quedan explícitos en el registro y no se presentan como cobertura ya lograda.
