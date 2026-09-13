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
  CANCELADA, RECHAZADA. (Nota: en la implementación actual, MODIFICADA es un
  estado transitorio dentro de `modificarDesdeCanal` — se reemplaza por
  CONFIRMADA o RECHAZADA en la misma operación, antes de guardar, así que no
  queda persistido como estado final.)
- Evitar duplicados usando la combinación (canal, referenciaExterna) como
  clave única — importante para reservas que llegan de una OTA, que puede
  reenviar el mismo evento más de una vez. Esta combinación además tiene una
  restricción única a nivel de base de datos (no solo en el código), y las
  operaciones de canal externo están protegidas contra condiciones de
  carrera (dos pedidos casi simultáneos para la misma reserva).

No calcula disponibilidad ni tarifas (eso es de ServicioDeInventarioYTarifas),
no procesa pagos (ServicioDePagos), no resuelve conflictos de overbooking
(ServicioDeOverbooking) y no envía notificaciones (ServicioDeNotificaciones).

## Tipo de componente

`ServicioDeReservasImpl` es `@Stateless`: cada operación recibe todos los
datos que necesita como parámetro; no hace falta conservar información entre
llamadas.

El componente realmente `@Stateful` del sistema es **`CarritoDeReserva`**
(`reservas/carrito/CarritoDeReserva.java`), también dentro de
ServicioDeReservas: el contenedor mantiene una instancia por sesión de
usuario, que recuerda hotel/fechas/huésped mientras se arma la reserva paso
a paso, con `@PostConstruct`/`@PreDestroy` marcando su ciclo de vida y
`@Remove` liberándola al confirmar.

ServicioDeInventarioYTarifas también maneja un concepto de estado (el hold),
pero NO es un EJB `@Stateful`: es `@Stateless`, y ese hold vive persistido
en la entidad `Hold` en base de datos, no en memoria de una instancia
conversacional (ver su propio README). Son dos formas distintas de manejar
estado — vale la pena poder explicar la diferencia si preguntan en la
defensa oral.

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
- **Consume** `com.stayhub.reservas.contrato.GestionDeDisponibilidadPort`, ya
  implementado por `ServicioDeInventarioYTarifasImpl`, con el mismo patrón
  de tolerancia a falta de dependencia (`Instance<T>` + `isResolvable()`)
  que usa ServicioDeCanalesExternos: si ServicioDeInventarioYTarifas alguna
  vez no estuviera disponible, las operaciones que necesitan disponibilidad
  responden `503 DEPENDENCIA_NO_DISPONIBLE`.

  > El puerto de solo lectura que definió Chiara
  > (`ServicioDeInventarioYTarifasPort`, con `consultarDisponibilidad` /
  > `consultarTarifas`) está pensado para que CanalesExternos publique
  > info en las OTAs, no para pedir/soltar un hold — por eso hace falta
  > este contrato adicional.

## Seguridad: dueño de la reserva

`cancelarReserva` y `modificarReserva` no dejan que cualquier usuario logueado
opere sobre cualquier reserva: un ADMIN puede cancelar o modificar cualquiera,
pero un HUESPED solo puede hacerlo sobre la SUYA (se compara el email cargado en
la reserva contra el usuario autenticado). Es una verificación programática,
porque el rol solo (`ADMIN`/`HUESPED`) no alcanza para expresar "esta reserva es
tuya" — hace falta lógica propia, no solo una anotación.

Quién está autenticado se resuelve en `ReservaResource`, no en el servicio: el
login de StayHub cambió de ser manejado por el contenedor de EJBs (HTTP Basic +
`SessionContext`) a un login por sesión propio (`POST /usuarios/login` guarda el
usuario en la `HttpSession`, y `AutenticacionFilter` la revisa en cada request).
Como el servicio ya no tiene forma de preguntarle al contenedor quién llama,
`ReservaResource` lee la sesión HTTP y pasa `actorEmail`/`actorEsAdmin` como
parámetros explícitos a `cancelarReserva`/`modificarReserva`, que los usan en un
helper interno (`verificarPropietario`) antes de tocar la reserva.

Cubierto por `ServicioDeReservasImplTest` (dueño, ajena, ADMIN, sin sesión) y por
la carpeta "03 - Seguridad" de la colección de Postman.

## Estructura

```
com.stayhub.reservas
├── api/           # capa de presentación (JAX-RS): ReservaResource, manejo de errores
├── contrato/       # puerto que ServicioDeReservas espera de InventarioYTarifas
├── dto/            # entrada/salida del API propio (ReservaRequest, ReservaResponse)
├── exception/       # ReservaException + CodigoErrorReserva
├── model/          # entidad Reserva (JPA), Huesped (embeddable), EstadoReserva
├── repository/      # patrón DAO: ReservaRepository + implementación JPA
└── service/         # ServicioDeReservasImpl: implementa ServicioDeReservasPort y ServicioDeReservas
```

## Pendiente / a coordinar con el equipo

- Cuándo y cómo se dispara ServicioDePagos al confirmar una reserva directa.
- Cómo se deriva un conflicto de disponibilidad hacia ServicioDeOverbooking
  (async, vía JMS) en lugar de rechazar directamente.
- Cómo se notifica un cambio de estado hacia ServicioDeNotificaciones (async).
