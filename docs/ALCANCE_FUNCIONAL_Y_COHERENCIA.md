# Alcance funcional vigente y coherencia documental

Fecha de revisión: 30 de octubre de 2026  
Tickets: KAN-102 y KAN-103

## Fuente de verdad

Este documento consolida el comportamiento de `development` y corrige diferencias entre el README,
el manual de usuario y la documentación técnica. Cuando una descripción anterior contradice esta
matriz, prevalecen el código desplegado y este alcance hasta que el equipo publique una versión
posterior.

## Direcciones por entorno

| Entorno | Aplicación | API | WSDL SOAP de StayHub |
| --- | --- | --- | --- |
| Docker Compose | `https://localhost:8443/StayHub/index.html` | `https://localhost:8443/StayHub/api` | `https://localhost:8443/StayHub/soap/integracion-pms?wsdl` |
| WildFly manual endurecido | `https://localhost:8080/StayHub/index.html` | `https://localhost:8080/StayHub/api` | `https://localhost:8080/StayHub/soap/integracion-pms?wsdl` |

El modo manual usa el puerto 8080 para HTTPS únicamente después de aplicar
`config/configurar-seguridad-local.py` o `config/https-8080.cli`. No se debe usar HTTP para la
aplicación ni asumir el puerto sin revisar la configuración de WildFly.

## Alcance implementado

| Función | Estado | Comportamiento vigente |
| --- | --- | --- |
| Catálogo de hoteles | Implementado | Administra hoteles, tipos y habitaciones con bajas lógicas. |
| Inventario y tarifas | Implementado | Gestiona cupo y precio por noche y holds persistidos. |
| Reserva directa y carrito | Implementado | El carrito conserva la selección por sesión; al confirmar crea reservas pendientes. |
| Reservas OTA | Implementado con simuladores | Las altas, modificaciones y cancelaciones REST delegan en Reservas y aplican idempotencia por canal y referencia. |
| Pago | Implementado con pasarela simulada | El pago aprobado confirma las reservas dentro de la misma transacción de negocio y luego publica eventos JMS. |
| Sincronización OTA y PMS | Implementado con simuladores | La API responde al encolado; un MDB publica por REST hacia OTAs o por SOAP hacia el PMS. |
| Overbooking | Implementado con alcance limitado | La resolución HTTP es síncrona; registra reubicación o compensación y encola la notificación por JMS. |
| Email | Implementado si existe SMTP | Usa STARTTLS y configuración externa. Los fallos se registran y pueden provocar reintentos JMS. |
| SMS y PUSH | Simulados | Validan la solicitud y registran el procesamiento, pero no contactan proveedores externos. |
| Usuarios y sesiones | Implementado | Registro de huésped, login, perfil, recuperación y control de roles y propiedad. |

## Funciones simuladas

- La pasarela aprueba montos válidos y genera una referencia `MOCK`; no realiza cobros bancarios.
- Booking, Expedia, Airbnb y Despegar se prueban con destinos REST simulados; no hay cuentas reales
  conectadas a esas redes.
- El PMS legado se prueba mediante `pms-legado-mock` y un contrato SOAP.
- SMS y PUSH sólo registran la operación. EMAIL puede ser real cuando el servidor SMTP está
  configurado fuera del repositorio.

## Fuera del alcance implementado o pendiente

| Elemento | Estado real | Consecuencia |
| --- | --- | --- |
| Favoritos | Fuera de alcance actual | El corazón de las tarjetas es decorativo; no existe endpoint, entidad ni persistencia de favoritos. |
| Vencimiento automático de holds | Pendiente | Un hold pendiente conserva cupo hasta que una operación lo confirma o libera. No existe temporizador de expiración. |
| Detección automática de overbooking | Pendiente | Un administrador debe enviar el conflicto a `/api/overbooking/resolver`. Reservas no dispara el flujo automáticamente. |
| Actualización integral de la reserva reubicada | Pendiente | Overbooking retiene una alternativa y registra la decisión, pero no modifica la reserva original ni libera su hold previo. |
| Cancelación anticipada por overbooking | Pendiente | La estrategia existe en el enum, pero el servicio sólo aplica reubicación o compensación. |
| Proveedores reales de SMS y PUSH | Fuera de alcance actual | No se entrega un mensaje fuera de StayHub. |
| Pasarela bancaria real | Fuera de alcance académico | No existe captura, devolución ni conciliación con un adquirente. |
| OTAs productivas | Fuera de alcance académico | Las integraciones externas requieren mocks o endpoints controlados. |
| Asignación de habitación física | Fuera de alcance actual | La reserva se realiza por tipo de habitación, no por número de habitación. |

Los elementos excluidos después del cierre de diseño deben validarse por escrito con la cátedra. La
clasificación técnica anterior no reemplaza esa aprobación. Hasta contar con evidencia, Jira y los
informes deben mostrarlos como pendientes de validación de alcance.

## Correcciones que deben reflejarse en el manual

1. La dirección principal es `https://localhost:8443` en Docker y `https://localhost:8080` en la
   instalación manual endurecida; no existe una única URL válida para ambos modos.
2. Un pago aprobado confirma las reservas de forma síncrona antes de responder. JMS se usa después
   para los correos de pago y confirmación.
3. Resolver overbooking es una operación síncrona iniciada por un administrador. Solamente la
   notificación posterior es asíncrona.
4. Una reubicación registra y confirma un hold alternativo, pero no modifica la reserva original ni
   libera el cupo previo.
5. Favoritos no forma parte de la versión actual aunque la interfaz muestre un icono de corazón.
6. Los holds no vencen automáticamente.
7. La pasarela, las OTAs, SMS y PUSH son simulados. El PMS también se demuestra con un mock. Email
   puede realizar una entrega real mediante SMTP.

## Configuración externa necesaria

- JPA usa `StayHubPU` y el datasource `java:/PostgresDS`.
- WildFly debe iniciarse con `standalone-full.xml` para disponer de JMS.
- Las OTAs requieren `stayhub.ota.<canal>.url`, token opcional y una lista explícita de hosts
  permitidos.
- El PMS saliente requiere `stayhub.pms.wsdl` y un host permitido.
- Email requiere propiedades SMTP privadas; las credenciales no se versionan.
- El entorno manual requiere su propio certificado, datasource y configuración de WildFly. Docker
  los prepara de forma reproducible para desarrollo.

## Evidencia técnica

- `ServicioDePagosImpl.cobrarYConfirmar` confirma cada reserva y después publica
  `EventoPagoAprobado`.
- `ServicioDeOverbookingImpl.resolverConflicto` resuelve y persiste antes de publicar la solicitud de
  notificación JMS.
- `SmsNotificador` y `PushNotificador` registran una entrega simulada.
- No existen recurso, entidad ni repositorio de Favoritos.
- No existe temporizador EJB ni tarea programada que expire holds.
- `compose.yaml` publica HTTPS en 8443; `config/https-8080.cli` configura el modo manual en 8080.
