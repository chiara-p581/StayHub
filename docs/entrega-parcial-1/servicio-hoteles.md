# ServicioDeHoteles - aporte para la Entrega Obligatoria N.º 1 del 14/09

## 1. Propósito del componente

ServicioDeHoteles administra el catálogo estructural y descriptivo de los establecimientos que
participan en StayHub. Permite crear, modificar, consultar y dar de baja hoteles, tipos de habitación
y habitaciones físicas. También registra la capacidad máxima de cada tipo, los servicios del hotel y
las características de tipos y habitaciones.

El componente expone, además, un puerto interno para que Inventario, Reservas y otros componentes
puedan validar que un hotel, un tipo o una habitación existen, están activos y pertenecen entre sí,
sin acceder directamente a las tablas ni depender de la API HTTP.

Su límite es deliberado: no calcula disponibilidad por fecha, cupos, tarifas, reservas ni
overbooking. Esas responsabilidades pertenecen a ServicioDeInventarioYTarifas,
ServicioDeReservas y ServicioDeOverbooking. Esta separación evita responsabilidades superpuestas.

## 2. Trabajo realizado

Para esta entrega se completaron y robustecieron los siguientes comportamientos:

- Alta, consulta, modificación, listado y baja lógica de hoteles.
- Alta, consulta, modificación, listado y baja lógica de tipos de habitación.
- Alta, consulta, modificación, listado y baja lógica de habitaciones físicas.
- Baja en cascada de los tipos y las habitaciones cuando se desactiva un hotel.
- Bloqueo de la baja de un tipo mientras todavía tenga habitaciones activas.
- Política de historial inmutable: un elemento dado de baja se puede consultar con fines de
  auditoría, pero no modificar ni reactivar.
- Validación de datos obligatorios, longitudes, identificadores positivos y capacidad máxima entera
  y positiva.
- Normalización a mayúsculas de códigos de tipo y números de habitación para evitar duplicados por
  diferencias de escritura.
- Restricciones únicas en PostgreSQL por hotel y traducción de conflictos concurrentes a una
  respuesta de negocio `409`, sin exponer detalles SQL.
- Respuestas de error JSON uniformes con `codigo`, `mensaje` y `fecha` para validaciones, recursos
  inexistentes, conflictos y cuerpos JSON incorrectos.
- Pruebas unitarias y una colección de Postman que cubren recorridos exitosos, validaciones, bajas
  lógicas y casos adversariales.

## 3. Interfaces explícitas

| Interfaz | Consumidor | Operaciones principales | Responsabilidad |
| --- | --- | --- | --- |
| `ServicioDeHoteles` | API REST y administración | Crear, modificar, consultar, listar y dar de baja hoteles, tipos y habitaciones | Facade de los casos de uso del componente |
| `ServicioDeHotelesPort` | Reservas, Inventario y otros componentes internos | Consultar hotel, validar hotel, tipo o habitación activa y consultar capacidad | Puerto interno de lectura sin dependencia HTTP |
| `HotelRepository` | Capa de negocio | Guardar, buscar, listar, verificar relaciones y sincronizar cambios | Contrato de acceso a datos |

Las interfaces de negocio intercambian DTOs y no exponen entidades JPA. Esto mantiene estable el
contrato del componente y evita que otros módulos conozcan relaciones lazy, consultas JPQL o detalles
de PostgreSQL.

## 4. Arquitectura en capas

| Capa | Elementos principales | Responsabilidad |
| --- | --- | --- |
| Presentación | `HotelResource`, `HotelExceptionMapper`, `JsonProcessingExceptionMapper`, DTOs | Adaptar HTTP y JSON, construir ubicaciones y traducir resultados y errores a códigos HTTP |
| Negocio | `ServicioDeHotelesImpl`, `ServicioDeHoteles`, `ServicioDeHotelesPort`, `HotelMapper` | Ejecutar casos de uso, reglas, validaciones, bajas lógicas y transformaciones |
| Datos | `HotelRepository`, `HotelRepositoryJpa`, `Hotel`, `TipoHabitacion`, `Habitacion` | Encapsular JPA y persistir el estado en PostgreSQL mediante `StayHubPU` |

```mermaid
flowchart LR
    HTTP[Cliente REST o Postman] --> API[HotelResource]
    API --> F[ServicioDeHoteles]
    INTERNOS[Reservas e Inventario] --> P[ServicioDeHotelesPort]
    F --> S[ServicioDeHotelesImpl Stateless]
    P --> S
    S --> M[HotelMapper]
    S --> D[HotelRepository]
    D --> J[HotelRepositoryJpa]
    J --> PG[(PostgreSQL)]
```

La presentación depende de la interfaz de negocio, la capa de negocio depende del contrato de datos
y solamente la implementación del repositorio conoce `EntityManager` y JPQL.

## 5. Tipo de componente y ciclo de vida

`ServicioDeHotelesImpl` se declara con `@Stateless`. Cada operación recibe todos los datos necesarios
y no conserva información conversacional entre pedidos. El estado duradero se almacena en PostgreSQL,
por lo que WildFly puede crear un pool de instancias y asignar cualquiera de ellas a cada solicitud.

La anotación EJB también evidencia la administración del ciclo de vida por parte del contenedor:
WildFly crea, reutiliza y destruye las instancias, realiza la inyección de dependencias y delimita las
transacciones. No se agregaron callbacks vacíos de inicialización o destrucción porque el componente
no necesita ejecutar una acción propia en esos momentos.

El componente stateful requerido por la entrega es una responsabilidad global del sistema. En los
cambios de `development` del 07/09 se incorporó `CarritoDeReserva` con `@Stateful`, `@PostConstruct`,
`@PreDestroy` y `@Remove`. Esa implementación debe integrarse antes de preparar la versión final.

## 6. Patrones aplicados y justificación

### Facade

`ServicioDeHoteles` presenta una entrada única y coherente a un subsistema formado por hoteles,
tipos, habitaciones, validaciones, mapeos y persistencia. `HotelResource` delega en esta Facade y no
coordina repositorios ni entidades. La implementación también ofrece `ServicioDeHotelesPort`, una
vista de lectura más acotada para otros componentes.

### DAO o Repository

`HotelRepository` define las operaciones de persistencia que necesita el negocio y
`HotelRepositoryJpa` las implementa mediante JPA. Las reglas no dependen de Hibernate, PostgreSQL ni
consultas JPQL concretas. Esta abstracción también permite probar el servicio con un repositorio doble.

### Data Mapper

`HotelMapper` transforma `Hotel`, `TipoHabitacion` y `Habitacion` en DTOs de respuesta. La API no
serializa entidades JPA directamente, con lo cual evita ciclos, relaciones lazy y filtración de
detalles internos.

Estos tres patrones responden a problemas reales del componente. No se agregó Factory o Strategy
de manera artificial porque actualmente no hay familias de objetos ni algoritmos intercambiables
que lo justifiquen.

## 7. Recursos y tecnologías utilizados

- Java 17 y Jakarta EE 10 como plataforma de desarrollo.
- EJB `@Stateless` para negocio, ciclo de vida y transacciones administradas por el contenedor.
- JAX-RS para exponer la API REST y CDI para inyectar dependencias.
- JPA con Hibernate para mapear y persistir el modelo.
- PostgreSQL mediante la unidad `StayHubPU` y el datasource `java:/PostgresDS`.
- WildFly 41 con el perfil `standalone-full.xml` como servidor de aplicaciones.
- Maven para compilar, ejecutar pruebas y generar `StayHub.war`.
- JUnit 5 para las pruebas automatizadas del componente.
- Postman para la demostración funcional y las pruebas de integración manuales.
- Git y una rama por funcionalidad para conservar un historial incremental.

## 8. Persistencia, transacciones y manejo de errores

Las operaciones públicas del EJB usan las transacciones administradas por WildFly. Una excepción
de negocio se declara con `@ApplicationException(rollback = true)`, de modo que una operación
inválida no deja cambios parciales.

Los tipos de habitación tienen una restricción única por `hotel_id` y `codigo`; las habitaciones,
por `hotel_id` y `numero`. Antes de terminar una creación o modificación sensible a duplicados, el
repositorio ejecuta `flush`. Así una carrera concurrente se detecta dentro del caso de uso y se
traduce al código de dominio correspondiente.

La capa REST devuelve `400` para solicitudes inválidas, `404` para elementos inexistentes y `409`
para duplicados, elementos inactivos o reglas de baja incumplidas. El cliente recibe un contrato de
error estable y no ve excepciones internas.

## 9. API y evidencia de prueba

La base local es `http://localhost:8080/StayHub/api/hoteles`. La API contiene quince operaciones
REST para hoteles, tipos y habitaciones. Los listados excluyen elementos inactivos por defecto y las
consultas directas los conservan visibles como evidencia histórica.

La colección `postman/StayHub-ServicioDeHoteles.postman_collection.json` contiene 30 solicitudes con
30 scripts de verificación. Incluye el recorrido completo y pruebas de duplicados, capacidad decimal,
JSON malformado, tipos JSON incorrectos, identificadores textuales, modificaciones después de una
baja y dos carreras concurrentes.

El 07/09 se verificó que el proyecto compila, ejecuta las 11 pruebas de hoteles sin fallas y genera
`StayHub.war`. El registro local de WildFly también evidencia un despliegue exitoso con PostgreSQL y
el registro del EJB `ServicioDeHotelesImpl`. Antes de la defensa se debe repetir el despliegue y la
colección completa después de integrar la versión más reciente de `development`.

## 10. Aporte a los requisitos del 14/09

| Requisito | Aporte de ServicioDeHoteles | Estado |
| --- | --- | --- |
| Tres componentes implementados y desplegados | ServicioDeHoteles aporta uno de los componentes completos | Cumple en su alcance; el total se demuestra con otros dos componentes del equipo |
| Arquitectura en capas | Presentación, negocio y datos están separadas por interfaces | Cumple |
| Un componente stateless | `ServicioDeHotelesImpl` usa `@Stateless` y no conserva estado conversacional | Cumple |
| Un componente stateful | No corresponde al catálogo; lo aporta `CarritoDeReserva` en `development` | Pendiente de integrar en esta rama |
| Tres patrones distintos | Facade, DAO o Repository y Data Mapper están implementados y justificados | Cumple en este componente |
| Seguridad declarativa | No pertenece a este componente; se aplica a la cancelación de reservas | Se verifica a nivel del sistema tras integrar `development` |
| Documento técnico de 5 a 8 páginas | Este texto documenta solamente el aporte de hoteles | Debe incorporarse al documento consolidado del equipo |
| Demostración en contenedor real | Existe evidencia local de despliegue; falta repetirla sobre el código integrado final | Revalidar antes de entregar |

## 11. Declaración de uso de inteligencia artificial

Se utilizó inteligencia artificial generativa como apoyo para auditar la correspondencia entre la
consigna, el código y las pruebas, y para revisar la claridad de esta documentación. Las afirmaciones
técnicas se contrastaron con el código fuente, la compilación, las pruebas automatizadas, la
colección de Postman y el registro de despliegue. La decisión de diseño y su defensa siguen siendo
responsabilidad del equipo.
