# Informe de prueba de carga — KAN-98

Fecha UTC: 2026-11-09T22:06:53.560Z
Carga: 50 usuarios virtuales concurrentes durante 5m.
Objetivo: p95 < 500 ms y tasa de error HTTP < 1%.

## Endpoints seleccionados

- `GET /hoteles`: entrada al catálogo y al embudo de reserva.
- `GET /inventario-tarifas/disponibilidad`: valida cupo antes de reservar.
- `GET /inventario-tarifas/tarifas`: obtiene el precio mostrado y usado por la reserva.

## Resultados

| Endpoint | p95 | Error HTTP | p95 < 500 ms | Error < 1% |
|---|---:|---:|:---:|:---:|
| GET /hoteles | 55.32 ms | 0.000% | Sí | Sí |
| GET /inventario-tarifas/disponibilidad | 24.70 ms | 0.000% | Sí | Sí |
| GET /inventario-tarifas/tarifas | 15.74 ms | 0.000% | Sí | Sí |

## Resultado global

- p95: 35.45 ms
- Tasa de error HTTP: 0.000%
- Checks correctos: 100.000%
- Veredicto: **APROBADO**
