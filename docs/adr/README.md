# Architecture Decision Records (ADR) — StayHub

Cada ADR documenta una decisión de diseño: el contexto que la motivó, la decisión
tomada, las alternativas descartadas y sus consecuencias. Formato basado en el
modelo de Michael Nygard (Contexto · Decisión · Alternativas · Consecuencias).

| ADR | Decisión | Patrón | Componente | Estado |
| --- | --- | --- | --- | --- |
| [ADR-001](ADR-001-state-ciclo-de-vida-reserva.md) | Ciclo de vida de la reserva con una tabla de transiciones | State | ServicioDeReservas | Aceptada |
| [ADR-002](ADR-002-facade-soap-pms.md) | Un único servicio SOAP como fachada para el PMS legado | Facade | IntegracionPms | Aceptada |
| [ADR-003](ADR-003-adapter-puertos-entre-componentes.md) | Puertos propios por consumidor y adaptadores hacia otros componentes y sistemas externos | Adapter | CanalesExternos, InventarioYTarifas | Aceptada |
| [ADR-004](ADR-004-dao-repository-acceso-a-datos.md) | Acceso a datos detrás de interfaces de repositorio | DAO / Repository | Todos los componentes con persistencia | Aceptada |
| [ADR-005](ADR-005-strategy-notificacion-por-canal.md) | Una estrategia de envío por canal de notificación | Strategy | ServicioDeNotificaciones | Aceptada |

Para agregar un ADR: copiar la estructura de uno existente, numerarlo en orden y
sumarlo a esta tabla. Un ADR aceptado no se edita para cambiar la decisión: se
escribe uno nuevo que lo reemplace y se marca el anterior como «Reemplazada por ADR-00X».
