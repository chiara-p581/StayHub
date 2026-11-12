# ADR-001: Ciclo de vida de la reserva con el patrón State

- **Estado:** Aceptada
- **Fecha:** 12/11/2026
- **Componente:** ServicioDeReservas
- **Patrón:** State (GoF)

## Contexto

La reserva es la entidad central de StayHub y pasa por varios estados: se crea
pendiente mientras se retiene el cupo, se confirma al aprobarse el pago, puede
modificarse o cancelarse, y puede ser rechazada (sin disponibilidad o por un
conflicto de overbooking). Cada operación sólo tiene sentido desde ciertos estados:
por ejemplo, no se puede confirmar una reserva cancelada ni volver a modificar una
cancelada.

Las reservas llegan por varios caminos (web, carrito, webhooks de OTAs, servicio SOAP
del PMS, pago aprobado). Si cada camino validara los estados por su cuenta, las reglas
quedarían repetidas y tarde o temprano se contradirían. Además, la cátedra exige que
toda transición inválida devuelva **HTTP 409 Conflict**.

## Decisión

El ciclo de vida se modela en el enum `EstadoReserva`
(`reservas/model/EstadoReserva.java`), con una **única tabla de transiciones**:

| Desde | Puede pasar a |
| --- | --- |
| PENDIENTE | CONFIRMADA, MODIFICADA, CANCELADA, RECHAZADA |
| CONFIRMADA | MODIFICADA, CANCELADA |
| MODIFICADA | PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA |
| RECHAZADA | MODIFICADA, CANCELADA |
| CANCELADA | (estado final) |

- Cada estado conoce sus destinos válidos (`transicionesPermitidas()`) y responde
  `permiteTransicionA(destino)`.
- La entidad `Reserva` no tiene setter de estado. Los cambios pasan por métodos de
  negocio (`confirmar()`, `cancelar()`, `rechazar()`, `modificar()`), que llaman a
  `transicionarA(destino)`. Si la tabla no lo permite, se lanza `ReservaException`
  con el código `TRANSICION_DE_ESTADO_INVALIDA`.
- `ReservaExceptionMapper` traduce ese código a **409 Conflict**.
- Repetir el mismo estado (por ejemplo, confirmar dos veces la misma reserva por un
  pago reintentado) no es una transición: es idempotente y no falla.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| `if`/`switch` sobre el estado dentro de cada método del servicio | Las reglas quedan dispersas en varios métodos y caminos de entrada (web, OTA, SOAP, pagos). Agregar un estado obliga a tocar todos. |
| Una clase por estado (State «clásico» de GoF, con interfaz `Estado` y una implementación por estado) | Con cinco estados y reglas que sólo dicen «a dónde se puede ir», genera muchas clases casi vacías. Además complica la persistencia con JPA, que guarda el estado como texto. El enum con comportamiento mantiene el patrón (cada estado decide sus transiciones) con menos código. |
| Validar transiciones con restricciones en la base de datos | La base no puede devolver un error de negocio legible ni un 409. La regla pertenece al dominio, no a la persistencia. |

## Consecuencias

**Positivas**
- Una sola fuente de verdad para las reglas del ciclo de vida: todos los caminos de
  entrada (REST, carrito, OTAs, SOAP, pagos) obedecen la misma tabla.
- El contrato HTTP es uniforme: toda transición inválida devuelve 409.
- Es fácil de probar exhaustivamente. `EstadoReservaTransicionesTest` recorre todas
  las combinaciones origen → destino con tests parametrizados (JUnit 5), como pide el
  criterio de testing del Eje B.

**Negativas / costos**
- Si un estado necesitara comportamiento propio más rico (no sólo «a dónde puede ir»),
  habría que migrar a una clase por estado.
- El enum queda acoplado a la entidad `Reserva`: un cambio en la tabla impacta a
  todos los consumidores a la vez (deseable para la coherencia, pero hay que
  comunicarlo).
