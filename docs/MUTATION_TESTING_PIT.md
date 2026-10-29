# Mutation testing con PIT (Eje B)

Evidencia para el Eje B de los Criterios de Testing (Clase 15, sección 5): mutation score ≥ 60% sobre el paquete de dominio. Mide si los tests realmente fallan cuando se rompe el código, algo que la cobertura de líneas no garantiza.

## Configuración

| Elemento | Detalle |
|---|---|
| Plugin | `pitest-maven` 1.19.1 con `pitest-junit5-plugin` 1.2.2, en [proyecto/pom.xml](../proyecto/pom.xml) |
| Alcance | `com.stayhub.*.service`, `com.stayhub.*.model` y `com.stayhub.reservas.carrito`. Es el mismo alcance que JaCoCo ([COBERTURA_JACOCO.md](COBERTURA_JACOCO.md)) |
| Fase | `verify`, goal `mutationCoverage` |
| Umbral | `mutationThreshold` 60: el build falla si el score es menor |
| Mutadores | Conjunto por defecto de PIT (condicionales, límites, aritmética, retornos, llamadas void) |
| Reporte | `proyecto/target/pit-reports/` (HTML y XML) |
| CI | Job `backend` de [.github/workflows/security.yml](../.github/workflows/security.yml): corre en `mvn verify`, escribe el score en el resumen del job y sube el reporte en el artefacto `backend-evidence`. El timeout del job se subió de 10 a 15 minutos |

Ejecución local: `mvn -f proyecto/pom.xml verify` (o `mvn test-compile org.pitest:pitest-maven:mutationCoverage` para correr solo PIT, unos 65 segundos).

## Resultado (2026-10-28)

| Medición | Mutantes | Muertos | Mutation score |
|---|---|---|---|
| Primera corrida, mutadores por defecto | 950 | 930 | 97,9% |
| Corrida exigente, mutadores `STRONGER` | 1.325 | 1.217 | 91,8% |
| **Tras los tests para sobrevivientes críticos, mutadores por defecto** | **950** | **938** | **98,7%** |

- Umbral exigido: 60%. El colchón es de casi 39 puntos.
- La cobertura de línea de las clases mutadas es de 98% (1325/1352).
- Suite completa: 666 tests, 0 fallos, 0 omitidos. El build completo (`verify` con tests, JaCoCo y PIT) tarda unos 90 segundos en local.
- **Verificación de que el pipeline falla:** con un pom temporal con umbral 100, PIT terminó en `BUILD FAILURE: Mutation score of 99 is below threshold of 100`. PIT redondea el score a entero.

## Revisión de mutantes sobrevivientes

La primera corrida dejó 20 sobrevivientes. Se revisaron uno por uno.

### Críticos: generaron tests nuevos (8 mutantes, 20 tests)

| Mutante | Qué no estaba verificado | Test nuevo |
|---|---|---|
| `ServicioDeReservasImpl.reemplazarHoldYConfirmar` L323 | Que modificar una reserva sin hold actualice sus datos | `ServicioDeReservasMutantesTest` |
| `ServicioDeReservasImpl.validarModificacion` L420 | Precio 0 aceptado y negativo rechazado | ídem |
| `ServicioDeReservasImpl.precio` L446 (2) | Límites exactos de total: escala 2/3 y precisión 12/13 | ídem |
| `PasswordHasher.verificar` L39 y L42 (3) | Límites de iteraciones (65.536 contra 65.535) y de salt/hash (16 contra 15 bytes) en código de seguridad | `PasswordHasherMutantesTest` |
| `ServicioDeUsuariosImpl.solicitarRecuperacionPassword` L147 | Email de 120 caracteres aceptado y de 121 rechazado | `RecuperacionPasswordMutantesTest` |
| `EnvioCorreoIdempotente.enviar` L42 | Que se persista y se haga `flush` del registro de idempotencia después de enviar, y no se reenvíe | `EnvioCorreoIdempotenteMutantesTest` |

### Sobrevivientes restantes (12): equivalentes o código muerto

| Mutante | Motivo por el que no se puede matar |
|---|---|
| `ServicioDeReservasImpl.buscarBloqueando` L361 y `buscarOFallar` L376 | `id <= 0` contra `id < 0`: con id 0 el repositorio devuelve vacío y el resultado es el mismo 404 |
| `ServicioDeReservasImpl.precio` L442 (2) y `validarDirecta` L438 | Llamadas a `Limites.cantidad` y `Limites.rango` repetidas: ya se validaron antes |
| `ServicioDeHotelesImpl` L156, L229, L235, L270 (4) | `validarId` repetido: ya lo hizo `hotelActivo` |
| `ServicioDeUsuariosImpl.autenticar` L80 | `Limites.texto` repetido |
| `ServicioDeCanalesExternosImpl.validarReserva` L96 | `cantidadHabitaciones < 1` es inalcanzable: `Limites.cantidad` lanza antes |
| `PasswordHasher.pbkdf2` L74 | `PBEKeySpec.clearPassword()` no es observable desde un test |

Estas validaciones repetidas son código muerto; limpiarlo es una mejora de código opcional (ver [COBERTURA_JACOCO.md](COBERTURA_JACOCO.md), observaciones sobre producción).

## Calidad y límites de esta evidencia

- El mutation score se calcula sobre las capas de negocio. API REST, repositorios, clientes y mensajería no se mutan; se cubren con las pruebas de integración, API y contrato.
- Con `STRONGER` el score es menor porque hay más mutantes de condicionales. Esa corrida se hizo antes de agregar los 20 tests críticos y no se repitió. Si la cátedra pide un conjunto más exigente, conviene repetirla.
- **Los tests agregados los generó Claude.** Antes de presentar, un integrante los revisa, los commitea con su autoría y puede explicarlos (Eje E, sección 8.1; declaración G8).
- La cátedra puede romper el código en vivo (invertir una condición, cambiar un límite). Con un score de 98,7%, casi cualquier cambio de ese tipo en la capa de negocio hace fallar un test. Las excepciones son los 12 casos de la tabla de equivalentes (validaciones repetidas) y cualquier cambio fuera del alcance de PIT (API, repositorios, clientes).
