# ServicioDeHoteles

Componente `@Stateless` que administra el catálogo estructural y descriptivo de StayHub.

Incluye alta, modificación, baja lógica y consulta de hoteles; gestión de tipos de habitación y
habitaciones físicas; capacidades, características y servicios; validación de pertenencia entre
hotel, tipo y habitación; persistencia mediante JPA; y endpoints REST bajo `/api/hoteles`.

La baja es lógica para no invalidar referencias históricas de reservas. Dar de baja un hotel también
desactiva sus tipos y habitaciones. Un tipo no puede darse de baja mientras tenga habitaciones activas.

El contrato `ServicioDeHotelesPort` permite que Reservas e Inventario validen hoteles, tipos y
habitaciones sin depender de la API REST.

Este componente no calcula disponibilidad por fecha, no administra cupos, no define tarifas, no crea
reservas y no decide sobre overbooking. Esas responsabilidades pertenecen a los otros componentes.

## Endpoints

- `POST /api/hoteles`, `GET /api/hoteles`, `GET|PUT|DELETE /api/hoteles/{id}`
- `POST /api/hoteles/{id}/tipos-habitacion`
- `PUT|DELETE /api/hoteles/{id}/tipos-habitacion/{tipoId}`
- `POST /api/hoteles/{id}/habitaciones`
- `PUT|DELETE /api/hoteles/{id}/habitaciones/{habitacionId}`
