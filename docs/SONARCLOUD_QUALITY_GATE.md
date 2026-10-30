# Análisis estático con SonarCloud y Quality Gate (Eje B)

Evidencia para el Eje B de los Criterios de Testing (Clase 15, sección 5): Quality Gate aprobado, 0 bugs Blocker/Critical, 0 vulnerabilidades, duplicación ≤ 3%, ratings A en confiabilidad y seguridad, 100% de security hotspots revisados.

## Estado

| Parte | Estado |
|---|---|
| Integración en el repositorio (pom, CI, script del gate) | Hecha |
| Código corregido hasta cumplir los criterios | Hecho, validado con SonarQube local (ver resultados) |
| Cuenta, organización y proyecto en SonarCloud, secreto y variables de GitHub | **Pendiente: requiere tu cuenta** (pasos abajo) |
| Run de CI con el Quality Gate de SonarCloud en verde | **Pendiente**, después de configurar lo anterior |

## Qué se agregó al repositorio

| Elemento | Detalle |
|---|---|
| [proyecto/pom.xml](../proyecto/pom.xml) | Propiedades `sonar.*`: fuentes (`src/main/java` y `src/main/webapp`), exclusión del CSS generado por Tailwind y de librerías de terceros, ruta del XML de JaCoCo y alcance de cobertura (el mismo que JaCoCo, ver [COBERTURA_JACOCO.md](COBERTURA_JACOCO.md)). La organización, la clave del proyecto y el token **no** están en el repo |
| [.github/workflows/security.yml](../.github/workflows/security.yml), job `backend` | `fetch-depth: 0`; paso de análisis con `sonar.qualitygate.wait=true`, que **hace fallar el pipeline si el Quality Gate no pasa**; resumen del gate en el job; `quality-gate.json` y `report-task.txt` en el artefacto `backend-evidence` (carpeta `sonar-evidence/`). Si faltan el secreto o las variables, el análisis se omite con una advertencia visible (no rompe el CI antes de configurar) |
| [sonar/crear-quality-gate.sh](../sonar/crear-quality-gate.sh) | Crea el gate **StayHub TP** por API, repetible, y lo asocia al proyecto |

### Quality Gate "StayHub TP"

Evalúa el **código completo**, no solo el nuevo (los gates nuevos vienen sembrados con condiciones de código nuevo; el script las elimina):

| Condición | Falla si |
|---|---|
| `reliability_rating` | Peor que A (A implica 0 bugs, así que 0 Blocker/Critical) |
| `security_rating` | Peor que A |
| `vulnerabilities` | Mayor que 0 |
| `duplicated_lines_density` | Mayor que 3% |
| `security_hotspots_reviewed` | Menor que 100% |

## Pasos para configurarlo en SonarCloud (los hace una persona del equipo)

1. Entrar a https://sonarcloud.io con la cuenta de GitHub, importar la organización y el repositorio **StayHub**.
2. En el proyecto: **Administration > Analysis Method**, desactivar *Automatic Analysis* (el análisis lo hace el CI).
3. En SonarCloud, **My Account > Security**, generar un token.
4. En GitHub, **Settings > Secrets and variables > Actions**:
   - Secreto `SONAR_TOKEN` con ese token.
   - Variables `SONAR_ORGANIZATION` (la clave de la organización) y `SONAR_PROJECT_KEY` (la clave del proyecto).
5. Crear el gate y asociarlo al proyecto:
   ```
   SONAR_TOKEN=... SONAR_HOST_URL=https://sonarcloud.io \
   SONAR_ORGANIZATION=<org> SONAR_PROJECT_KEY=<proyecto> ./sonar/crear-quality-gate.sh
   ```
   Si el plan de SonarCloud no permite gates propios, dejar *Sonar way* y verificar a mano en el dashboard que se cumplan los criterios de la tabla de arriba (los resultados locales indican que se cumplen).
6. Hacer push y revisar que el job `backend` pase. Pegar abajo el link al run y la captura del dashboard (la cátedra pide la captura del Quality Gate en el informe de ejecución).

Link al run en verde: _pendiente_
Link al dashboard de SonarCloud: _pendiente_

## Resultados de la validación local (2026-10-30)

No pude usar SonarCloud, así que validé con SonarQube Community Build 26.9 en local, con el mismo motor de análisis y el perfil *Sonar way* de Java, JavaScript, CSS y HTML/JSP. **SonarCloud puede reportar reglas distintas o adicionales** (su versión de las reglas puede diferir), por eso el primer run real puede traer hallazgos nuevos que habrá que revisar.

| Métrica | Primer análisis | Después de corregir | Criterio |
|---|---|---|---|
| Bugs | 23 | **0** | 0 Blocker/Critical |
| Vulnerabilidades | 6 | **0** | 0 |
| Rating de confiabilidad | D | **A** | A |
| Rating de seguridad | C | **A** | A |
| Duplicación | 0,6% | **0,6%** | ≤ 3% |
| Security hotspots | 0 | **0** | 100% revisados (no hay hotspots que revisar) |
| Mantenibilidad | A | A | n/a |
| Cobertura (alcance de negocio) | 95,5% | 95,5% | n/a |

Quality Gate **StayHub TP**: pasa (`BUILD SUCCESS`). Cuando un gate falla, el análisis con `sonar.qualitygate.wait=true` termina en `BUILD FAILURE`: se vio en una corrida intermedia, en la que el gate tenía una condición de código nuevo que no se cumplía. Es lo que hará fallar el CI.

### Correcciones hechas

**Java de producción**

| Regla | Dónde | Corrección |
|---|---|---|
| S2654 (Critical): `synchronized` en un EJB | `ServicioDeReservasImpl` (3 métodos de canal) | Se mantiene la exclusión por referencia con un `ReentrantLock` por ranura, en lugar de `synchronized` |
| S2119 (Critical): `Random` nuevo en cada llamada | `TokenRecuperacion` | `SecureRandom` estático reutilizado |
| S2445: sincronizar sobre un parámetro | `Csrf.token` | Candado estático propio |
| S2259: posible NPE | `ConfiguracionCanales.esLocal` | Comparaciones seguras ante null, sin cambiar el resultado |
| S2077 (4 vulnerabilidades): consulta JPQL armada por concatenación | `HotelRepositoryJpa` | Dos consultas literales completas. Nunca había entrada del usuario en la concatenación: era un falso positivo, pero se reescribió para eliminarlo |

**Tests**: S9351 (2 bugs) en `CarritoDeReservaTest` y `ServicioDeReservasCanalTest`: `BigDecimal.equals` reemplazado por `compareTo`.

**Frontend** (sin cambios de diseño): 10 inputs sin label asociado (se agregaron `for` a labels existentes o `aria-label`), `index.jsp` sin `DOCTYPE`, `title` ni `lang`, dos fuentes CSS sin familia genérica y dos usos de `Math.random` (S2245) reemplazados por `crypto.getRandomValues`.

Verificación tras los cambios: 666 tests en verde, JaCoCo y PIT en verde (PIT 99%), tests del frontend 24/24.

## Limitaciones y puntos a revisar

- **Code smells:** quedan 445 (304 Major, 37 Critical, 52 Minor, 52 Info). La cátedra no los incluye en el gate. Conviene mirar los 37 Critical (suelen ser complejidad cognitiva) por si preguntan.
- **El análisis cubre `proyecto/`** (backend Java y frontend web). No cubre `pms-legado-mock` ni los scripts de `contracts/`.
- **`aria-label` como label:** el análisis local lo aceptó para la regla `InputWithoutLabelCheck`. Si SonarCloud lo marcara, cambiar esos 4 inputs a un `<label for>` oculto visualmente.
- **Hotspots:** hoy no hay ninguno. Si SonarCloud abre alguno, hay que revisarlo y marcarlo (seguro o corregido) desde el dashboard antes de la prueba: sin eso falla la condición de 100% revisados.
- **Tests y código generados con IA:** parte de estas correcciones las hizo Claude. Quien presente debe poder explicarlas (Eje E, declaración G8).
