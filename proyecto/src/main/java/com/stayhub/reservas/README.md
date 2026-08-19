# ServicioDeReservas

Componente responsable de crear y administrar las reservas de StayHub, tanto
las hechas directamente en la plataforma como las que llegan empujadas desde
canales externos a través de ServicioDeCanalesExternos.

## Responsabilidades

- Crear, modificar y cancelar reservas.
- Validar que los datos de la reserva sean consistentes (fechas, cantidad de
  habitaciones, huésped, precio).
- Retener disponibilidad (hold) contra ServicioDeInventarioYTarifas antes de
  confirmar, y liberarla si la reserva se cancela o se modifica.
- Administrar el ciclo de estados: PENDIENTE, CONFIRMADA, MODIFICADA,
  CANCELADA, RECHAZADA.
- Evitar duplicados usando la combinación (canal, referenciaExterna) como
  clave única — importante para reservas que llegan de una OTA, que puede
  reenviar el mismo evento más de una vez.

No calcula disponibilidad ni tarifas (eso es de ServicioDeInventarioYTarifas),
no procesa pagos (ServicioDePagos), no resuelve conflictos de overbooking
(ServicioDeOverbooking) y no envía notificaciones (ServicioDeNotificaciones).

## Tipo de componente

`@Stateless`. Cada operación recibe todos los datos que necesita como
parámetro; no hace falta conservar información entre llamadas. El componente
stateful del sistema es ServicioDeInventarioYTarifas (mantiene el hold
abierto internamente); ServicioDeReservas solo le pide y libera holds por
id, sin guardar ese estado conversacional de su lado.

## Dos caminos de entrada, una sola lógica de negocio

**Directo (`ServicioDeReservas`, vía `ReservaResource` / `/api/reservas`)**
Un usuario reserva en StayHub. Flujo en dos pasos: `crearReserva` retiene el
hold y deja la reserva en PENDIENTE; `confirmarReserva` confirma el hold y
pasa la reserva a CONFIRMADA (por ejemplo, cuando se completa el pago).

**Por canal externo (`ServicioDeReservasPort`, implementado para
ServicioDeCanalesExternos)** Una OTA informa una reserva ya decidida de su
lado. El contrato (`crearDesdeCanal` / `modificarDesdeCanal` /
`cancelarDesdeCanal`) no tiene un paso de confirmación separado, así que acá
se resuelve todo en una sola llamada: se pide el hold y se confirma en el
mismo paso, o se rechaza si no hay disponibilidad.

Ambos caminos comparten la misma entidad `Reserva` y el mismo repositorio;
lo único que cambia es el mapeo de entrada/salida (ver `ReservaMapper`).

## Contratos

- **Implementa** `com.stayhub.canalesexternos.contrato.interno.ServicioDeReservasPort`
  (definido por ServicioDeCanalesExternos). Se registra como bean CDI/EJB
  para que WildFly lo descubra automáticamente vía
  `Instance<ServicioDeReservasPort>`.
- **Consume** (propuesta, a confirmar con quien implemente
  ServicioDeInventarioYTarifas) `com.stayhub.reservas.contrato.GestionDeDisponibilidadPort`,
  con el mismo patrón de tolerancia a falta de dependencia
  (`Instance<T>` + `isResolvable()`) que usa ServicioDeCanalesExternos:
  mientras ServicioDeInventarioYTarifas no exista, las operaciones que
  necesitan disponibilidad responden `503 DEPENDENCIA_NO_DISPONIBLE`.

  > El puerto de solo lectura que ya definió Chiara
  > (`ServicioDeInventarioYTarifasPort`, con `consultarDisponibilidad` /
  > `consultarTarifas`) está pensado para que CanalesExternos publique
  > info en las OTAs, no para pedir/soltar un hold — por eso hace falta
  > este contrato adicional.

## Estructura

```
com.stayhub.reservas
├── api/           # capa de presentación (JAX-RS): ReservaResource, manejo de errores
├── contrato/       # puerto que ServicioDeReservas espera de InventarioYTarifas (propuesta)
├── dto/            # entrada/salida del API propio (ReservaRequest, ReservaResponse)
├── exception/       # ReservaException + CodigoErrorReserva
├── model/          # entidad Reserva (JPA), Huesped (embeddable), EstadoReserva
├── repository/      # patrón DAO: ReservaRepository + implementación JPA
└── service/         # ServicioDeReservasImpl: implementa ServicioDeReservasPort y ServicioDeReservas
```

## Pendiente / a coordinar con el equipo

- Confirmar con quien haga ServicioDeInventarioYTarifas el contrato real de
  `GestionDeDisponibilidadPort` (nombres de métodos, si el hold expira solo
  o hay que liberarlo explícitamente, etc.).
- `persistence.xml` / nombre de la unidad de persistencia (`stayhubPU` es un
  placeholder).
- Cuándo y cómo se dispara ServicioDePagos al confirmar una reserva directa.
- Cómo se deriva un conflicto de disponibilidad hacia ServicioDeOverbooking
  (async, vía JMS) en lugar de rechazar directamente.
- Cómo se notifica un cambio de estado hacia ServicioDeNotificaciones (async).
