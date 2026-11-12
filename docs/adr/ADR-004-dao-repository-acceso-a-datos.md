# ADR-004: Acceso a datos detrás de interfaces de repositorio

- **Estado:** Aceptada
- **Fecha:** 12/11/2026
- **Componentes:** todos los que persisten (Reservas, InventarioYTarifas, Pagos, Hoteles, Usuarios, Overbooking)
- **Patrón:** DAO / Repository

## Contexto

La consigna exige una arquitectura en capas explícita (presentación, negocio y datos)
en cada componente. StayHub persiste en PostgreSQL con JPA, y el acceso a datos tiene
detalles propios de la infraestructura que no deberían mezclarse con las reglas de
negocio:

- Consultas JPQL y nativas (por ejemplo, llegadas del día por hotel, o reservas
  pendientes vencidas).
- **Bloqueos para concurrencia:** `PESSIMISTIC_WRITE` al confirmar o pagar una
  reserva, `SELECT … FOR UPDATE` sobre el inventario diario y *advisory locks* de
  PostgreSQL para la idempotencia de pagos.
- Control de versión optimista (`@Version`), que respalda el ETag/If-Match de la API
  (KAN-96).
- Búsquedas por clave de idempotencia y por canal + referencia externa (reservas de OTAs).

Además, los tests unitarios de negocio tienen que poder ejecutarse sin base de datos
ni contenedor.

## Decisión

Cada componente define una **interfaz de repositorio** en su paquete `repository/`, con
operaciones expresadas en el lenguaje del dominio, y una **implementación JPA**:

| Interfaz | Implementación |
| --- | --- |
| `reservas/repository/ReservaRepository` | `ReservaRepositoryJpa` |
| `inventarioytarifas/repository/InventarioDiarioRepository` | `InventarioDiarioRepositoryJpa` |
| `inventarioytarifas/repository/HoldRepository` | `HoldRepositoryJpa` |
| `pagos/repository/PagoRepository` | `PagoRepositoryJpa` |
| `hoteles/repository/HotelRepository` | `HotelRepositoryJpa` |
| `usuarios/repository/UsuarioRepository` | `UsuarioRepositoryJpa` |
| `servicioDeOverbooking/repository/ConflictoOverbookingRepository` | `ConflictoOverbookingRepositoryJpa` |

- Los servicios de negocio (`ServicioDeReservasImpl`, etc.) dependen **sólo de la
  interfaz**, inyectada con CDI. Nunca usan `EntityManager` directamente.
- Las operaciones nombran la intención del negocio, no la técnica:
  `buscarPorIdBloqueando`, `buscarPorUsuarioYClaveIdempotencia`, `listarLlegadas`,
  `pendientesVencidas`.
- Los detalles de JPA (bloqueos, JPQL, consultas nativas, `flush` para que `@Version`
  se incremente antes de armar el ETag) quedan encapsulados en la implementación.
- Las transacciones las define la capa de negocio con `@TransactionAttribute` (EJB),
  no el repositorio.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Usar `EntityManager` directamente en los servicios | Mezcla negocio y persistencia, rompe la separación en capas y obliga a levantar JPA para probar cualquier regla de negocio. |
| Un repositorio genérico único (`Repository<T, ID>`) para todas las entidades | Las consultas importantes son específicas (bloqueos, idempotencia, llegadas). Un genérico termina con métodos que no aplican a todas las entidades o con consultas armadas en el servicio. |
| Spring Data / Jakarta Data con métodos derivados | El stack elegido es Jakarta EE sobre WildFly con EJB y JPA estándar. Además, los bloqueos y las consultas nativas necesitan control explícito igual. |

## Consecuencias

**Positivas**
- La lógica de negocio se prueba con repositorios simulados (Mockito), sin base de
  datos. Así funcionan `ServicioDeReservasImplTest`, `ServicioDePagosImplTest`, etc., y
  eso permite la cobertura y el mutation score del Eje B.
- Las decisiones de concurrencia (bloqueos, versión optimista) quedan en un solo lugar
  por entidad y son revisables.
- Cambiar de motor de base de datos o de estrategia de consulta impacta sólo a la
  implementación JPA.

**Negativas / costos**
- Una interfaz más por entidad persistida.
- Hay detalles que dependen de PostgreSQL (advisory locks, `FOR UPDATE`) dentro de las
  implementaciones: la abstracción oculta el motor al negocio, pero no hace al sistema
  portable a cualquier base sin cambios.
- Las implementaciones JPA necesitan pruebas de integración contra una base real.
  Hoy las cubre el entorno Docker del job *Integración Docker*.
