# ServicioDeInventarioYTarifas

Componente responsable de la capacidad (cantidad de habitaciones por tipo) y
las tarifas de cada hotel, día por día, y de retener ("hold") ese cupo
mientras una reserva está pendiente de confirmación.

## Responsabilidades

- Cargar/actualizar capacidad y tarifa de un tipo de habitación de un hotel
  para un rango de fechas (`cargarInventario`).
- Consultar disponibilidad y tarifas de un hotel para un rango de fechas.
- Retener cupo (hold) para ServicioDeReservas mientras una reserva está
  pendiente, y confirmarlo o liberarlo según el resultado.

No crea ni administra reservas (eso es de ServicioDeReservas), no procesa
pagos (ServicioDePagos), no resuelve conflictos de overbooking
(ServicioDeOverbooking) y no envía notificaciones (ServicioDeNotificaciones).

## Tipo de componente

`@Stateless`, como el resto de los servicios del proyecto — pero es el único
componente conceptualmente "stateful" del sistema: un hold creado en
`crearHold` sigue existiendo y sigue descontando cupo hasta que una llamada
posterior e independiente (`confirmarHold` / `liberarHold`) lo cierra. Ese
estado no se guarda en memoria de instancia, sino en la entidad `Hold`
persistida vía `HoldRepository` — el propio EJB sigue sin conservar nada
entre invocaciones.

## Modelo

- `InventarioDiario`: capacidad total, capacidad ocupada y tarifa de un tipo
  de habitación de un hotel para un día puntual. Es la unidad mínima; la
  disponibilidad/tarifa de un rango se calcula agregando una fila por noche.
- `Hold`: retención de cupo pedida por ServicioDeReservas, con id de clave
  natural (UUID) para poder referenciarla en llamadas posteriores.

## Contratos

Implementa tres puertos ya definidos por otros componentes, a la espera de
esta implementación:

- `com.stayhub.canalesexternos.contrato.interno.ServicioDeInventarioYTarifasPort`
  (`consultarDisponibilidad` / `consultarTarifas`, de solo lectura, para que
  ServicioDeCanalesExternos publique en las OTAs).
- `com.stayhub.servicioDeOverbooking.contrato.interno.ServicioDeInventarioYTarifasPort`
  (`consultarDisponibilidad`, para que ServicioDeOverbooking busque una
  alternativa al resolver un conflicto).
- `com.stayhub.reservas.contrato.GestionDeDisponibilidadPort`
  (`crearHold` / `confirmarHold` / `liberarHold`, para que ServicioDeReservas
  retenga y suelte cupo).

Los dos primeros declaran un `consultarDisponibilidad(Long, LocalDate, LocalDate)`
con el mismo borrado de firma pero devolviendo cada uno su propio
`DisponibilidadDTO` — tipos de retorno incompatibles entre sí — así que una
sola clase no puede implementar ambas interfaces (restricción del lenguaje,
no del diseño). Por eso `ServicioDeInventarioYTarifasImpl` implementa
directamente `ServicioDeInventarioYTarifas` (contrato propio) y
`GestionDeDisponibilidadPort`, y dos adaptadores finos
(`AdaptadorPortCanalesExternos`, `AdaptadorPortOverbooking`) delegan en ella
para los otros dos puertos, traduciendo únicamente los DTOs.

Se registra como bean CDI/EJB (los tres adaptadores/implementaciones) para
que cada consumidor lo descubra vía `Instance<Puerto>` + `isResolvable()`,
mismo patrón que ya usan ServicioDeCanalesExternos, ServicioDeOverbooking y
ServicioDeReservas.

## API REST

Base: `/StayHub/api/inventario-tarifas`

- `GET /inventario-tarifas/disponibilidad?hotelId=&desde=&hasta=`
- `GET /inventario-tarifas/tarifas?hotelId=&desde=&hasta=`
- `POST /inventario-tarifas/cargas`

Las fechas usan ISO-8601 (`AAAA-MM-DD`); los rangos son `[desde, hasta)`
(noches), igual que en ServicioDeReservas y ServicioDeCanalesExternos.

## Estructura

```
com.stayhub.inventarioytarifas
├── api/         # capa de presentación (JAX-RS): InventarioResource, manejo de errores
├── contrato/    # ServicioDeInventarioYTarifas: contrato propio
├── dto/         # entrada/salida del API propio
├── exception/   # InventarioTarifasException + CodigoErrorInventarioTarifas
├── model/       # InventarioDiario y Hold (JPA), EstadoHold
├── repository/  # patrón DAO: InventarioDiarioRepository / HoldRepository + implementaciones JPA
└── service/     # implementación del núcleo + los dos adaptadores de puertos
```
