# Control de concurrencia con ETag en reservas (KAN-96)

## Problema

Dos personas (por ejemplo, el huésped y un administrador) abren la misma
reserva y la modifican casi al mismo tiempo. Sin control, el último en
guardar pisa el cambio del otro sin enterarse ("lost update").

## Solución: concurrencia optimista con ETag / If-Match

La entidad `Reserva` ya tenía un campo `@Version` (JPA lo incrementa en cada
cambio). Ahora esa versión viaja en los headers HTTP:

| Paso | Pedido | Respuesta |
| --- | --- | --- |
| 1. Consultar | `GET /api/reservas/7` | `200` + `ETag: "3"` |
| 2. Modificar con la versión leída | `PUT /api/reservas/7` + `If-Match: "3"` | `200` + `ETag: "4"` |
| 3. Otro cliente con la versión vieja | `PUT /api/reservas/7` + `If-Match: "3"` | `412 Precondition Failed` (`VERSION_OBSOLETA`) |
| Sin header | `PUT /api/reservas/7` sin `If-Match` | `428 Precondition Required` (`VERSION_REQUERIDA`) |
| Forzar | `PUT /api/reservas/7` + `If-Match: *` | se aplica sin chequear versión |

Orden de validaciones en `modificarReserva`: primero existencia y permisos
(404/403, para no revelar datos de reservas ajenas), después la versión
(428/412) y recién ahí las reglas de negocio.

## Por qué el dato más reciente no se pisa

1. `buscarBloqueando` toma un lock pesimista sobre la fila: si dos `PUT`
   llegan a la vez, el segundo espera a que termine el primero.
2. Cuando el segundo entra, la versión en base ya cambió y no coincide con su
   `If-Match` → `412`, sin tocar cupo, precio ni datos.
3. Después de guardar se hace `flush` (`ReservaRepository.sincronizar()`) para
   que el `ETag` devuelto sea la versión nueva y el cliente pueda seguir
   editando sin volver a consultar.

## Archivos

- `reservas/api/ETagReserva.java`: formatea el `ETag` y parsea `If-Match`.
- `reservas/api/ReservaResource.java`: `GET` y `PUT /{id}` con headers.
- `reservas/service/ServicioDeReservasImpl.java`: `verificarVersion(...)`.
- `reservas/exception/CodigoErrorReserva.java` + `ReservaExceptionMapper`:
  `VERSION_OBSOLETA → 412`, `VERSION_REQUERIDA → 428`.
- `reservas/dto/ReservaResponse.java`: nuevo campo `version`.

## Tests (criterios de aceptación)

`ConcurrenciaETagReservaTest`:

- `etagVigenteSeAplica` → criterio 1.
- `etagObsoletoDevuelve412` → criterio 2.
- `elDatoMasRecienteNoSeSobrescribe` → criterio 3 (dos clientes leen la
  versión 0, el primero guarda, el segundo recibe 412 y el cambio del primero
  se conserva).
- Extra: 428 sin header, `If-Match: *`, autorización antes que versión.

`ETagReservaTest`: formato del ETag y parseo de `If-Match` (válidos e inválidos).
