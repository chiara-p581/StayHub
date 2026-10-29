# Cobertura de código con JaCoCo (Eje B)

Evidencia para el Eje B de los Criterios de Testing (Clase 15, sección 5): cobertura de líneas ≥ 80% y de ramas ≥ 70% en la capa de negocio/dominio, con el pipeline fallando si no se alcanzan.

## Configuración

| Elemento | Detalle |
|---|---|
| Plugin | `jacoco-maven-plugin` 0.8.13 en [proyecto/pom.xml](../proyecto/pom.xml) |
| `prepare-agent` | Instrumenta la corrida de Surefire |
| `report` (fase `test`) | Genera HTML, XML y CSV en `proyecto/target/site/jacoco/` |
| `check` (fase `verify`) | Falla el build (`BUILD FAILURE`) si no se cumplen los umbrales |
| Umbrales | Líneas ≥ 0,80 y ramas ≥ 0,70, regla `BUNDLE` sobre el alcance de abajo |
| CI | Job `backend` de [.github/workflows/security.yml](../.github/workflows/security.yml): corre `mvn clean verify`, escribe un resumen de cobertura en el resumen del job y sube `target/site/jacoco/` y `jacoco.exec` en el artefacto `backend-evidence` |

### Alcance: capa de negocio/dominio

Incluye `com.stayhub.*.service`, `com.stayhub.*.model` y `com.stayhub.reservas.carrito`.

Quedan fuera API REST, DTO, repositorios JPA, clientes externos, mensajería, configuración, seguridad HTTP y datos de demo. Esas capas no son lógica de negocio y se cubren con las pruebas de integración, API y contrato de los demás criterios del Eje B. Si la cátedra define la capa de otra forma, se ajustan los `<include>` del pom.

## Resultado (2026-10-28)

| Métrica | Antes | Ahora | Umbral |
|---|---|---|---|
| Líneas | 52,6% (719/1367) | 98,4% (1345/1367) | 80% |
| Ramas | 31,9% (244/764) | 90,8% (694/764) | 70% |
| Tests | 117 | 646, 0 fallos, 0 omitidos | |

Se apuntó a un colchón de unos 10 puntos sobre los umbrales para que cambios posteriores no rompan el pipeline.

**Verificación de que el pipeline falla:** con un pom temporal que exigía 99% de líneas y 95% de ramas, `jacoco:check` terminó en `BUILD FAILURE` (exit 1) con `Rule violated for bundle StayHub: lines covered ratio is 0.98, but expected minimum is 0.99`.

## Calidad de los tests agregados

- 23 archivos nuevos, 473 métodos de test, sin `@Disabled`, sin `assertTrue(true)` y sin ningún test sin aserciones o `verify`.
- Solo se mockean colaboradores (repositorios, puertos, publicadores), nunca la clase bajo prueba.
- Predominan los casos negativos y de borde: nulos, límites exactos, estados inválidos, errores del sistema externo.
- Los tests los generó Claude. **Antes de presentar, un integrante tiene que revisarlos, poder explicarlos y commitearlos con su autoría de Git** (Eje E, sección 8.1). Registrar la herramienta y qué se revisó en la declaración de uso de IA (G8).

## Limitaciones conocidas

- **Líneas sin cubrir (22):** son sobre todo constructores `protected` vacíos exigidos por JPA y un `catch` de algoritmo no disponible.
- **Ramas sin cubrir (70):** son en su mayoría condiciones inalcanzables. Varios servicios llaman primero a `Limites.*`, que lanza antes de que se evalúen las validaciones propias; esas validaciones quedaron como código muerto.
- **La cobertura no prueba calidad.** Falta el mutation score (≥ 60%, PIT), que es el control que detecta tests que no verifican nada.
- Los tests de `PlantillaEmail` dependen del formato regional es-AR del JDK. Pasan con el JDK 17 de CI y local.

## Observaciones sobre el código de producción (no corregidas)

Detectadas al escribir los tests. Candidatas a defectos para el registro de QA:

1. **Validaciones duplicadas e inalcanzables** en los servicios de inventario, reservas, usuarios, canales y overbooking.
2. **Errores distintos para el mismo tipo de dato inválido:** según el campo se devuelve `SeguridadException` (400) o la excepción del módulo con `SOLICITUD_INVALIDA`.
3. `ServicioDeReservasImpl.listarPorHuespedEmail` ignora el parámetro `email` y devuelve las reservas del usuario de la sesión.
4. `ServicioDeUsuariosImpl.registrar` no normaliza el email: `Ana@x.com` y `ana@x.com` pasan como cuentas distintas salvo que la base lo impida.
5. `actualizarCapacidadYTarifa` permite bajar `unidadesTotales` por debajo de `unidadesOcupadas` (disponibilidad negativa).
6. Los holds de inventario no vencen: un hold `PENDIENTE` retiene cupo indefinidamente.
7. `ServicioDeOverbookingImpl.disponibilidad()` informa un mensaje copiado y confuso ("ServicioDeInventarioYTarifas todavía no posee una implementación").
8. La cuota de notificaciones usa un `LimitadorSolicitudes` estático compartido por todo el proceso.
9. `ConflictoOverbooking.resolverPorCompensacion` no limpia la alternativa ni el hold de una resolución anterior.
