# ServicioDeCanalesExternos

Componente de integración de StayHub. Expone webhooks/endpoints REST para OTAs, publica
inventario y tarifas mediante JAX-RS Client y sincroniza un PMS legado mediante JAX-WS/SOAP.
No calcula disponibilidad, tarifas ni reglas de reservas.

Las solicitudes de sincronización se publican en una cola JMS y se procesan de forma
asincrónica mediante un Message-Driven Bean. La respuesta confirma que la solicitud fue
encolada, no que el sistema externo ya haya recibido los datos. Si el procesamiento falla,
la transacción del consumidor se revierte para que el proveedor JMS pueda aplicar su política
de reentrega.

## Contratos internos

- `ServicioDeReservasPort`: crear, modificar y cancelar una reserva originada en un canal.
- `ServicioDeInventarioYTarifasPort`: consultar disponibilidad y tarifas para publicarlas.

Los componentes correspondientes deben implementar estas interfaces como beans CDI/EJB. Hasta
entonces, el despliegue continúa funcionando y las operaciones dependientes responden HTTP 503.

## API REST

La base de la aplicación es `/StayHub/api`.

- `GET /canales-externos/disponibilidad?hotelId=&desde=&hasta=`
- `POST /canales-externos/otas/{canal}/reservas`
- `PUT /canales-externos/otas/{canal}/reservas/{idExterno}`
- `DELETE /canales-externos/otas/{canal}/reservas/{idExterno}`
- `POST /canales-externos/otas/{canal}/sincronizaciones?hotelId=&desde=&hasta=`
- `POST /canales-externos/pms/sincronizaciones?hotelId=&desde=&hasta=`

Las fechas usan ISO-8601 (`AAAA-MM-DD`). Los canales iniciales son `BOOKING`, `EXPEDIA`,
`AIRBNB`, `DESPEGAR` y `OTRO`.

## Seguridad

Toda la API de Canales Externos exige autenticación HTTP BASIC contra el realm de
WildFly configurado para la aplicación. Las consultas de disponibilidad y las
sincronizaciones requieren el rol `ADMIN`. Los webhooks de reservas OTA permiten
`CANAL_EXTERNO` y `ADMIN`.

Para dar de alta una cuenta técnica de una OTA se ejecuta `add-user.bat` en WildFly,
se elige `Application User` y se asigna el grupo `CANAL_EXTERNO`. Las llamadas deben
enviar esa cuenta mediante el encabezado `Authorization: Basic ...`; nunca se deben
incluir esas credenciales en el código ni en el repositorio.

## Configuración

Puede suministrarse como propiedad de sistema de WildFly o variable de entorno:

| Propiedad | Variable equivalente | Uso |
|---|---|---|
| `stayhub.ota.booking.url` | `STAYHUB_OTA_BOOKING_URL` | URL base de Booking |
| `stayhub.ota.booking.token` | `STAYHUB_OTA_BOOKING_TOKEN` | Bearer token opcional |
| `stayhub.pms.wsdl` | `STAYHUB_PMS_WSDL` | URL del WSDL del PMS |
| `stayhub.canales.timeout.conexion.ms` | `STAYHUB_CANALES_TIMEOUT_CONEXION_MS` | Timeout de conexión REST/SOAP (default 5000 ms) |
| `stayhub.canales.timeout.lectura.ms` | `STAYHUB_CANALES_TIMEOUT_LECTURA_MS` | Timeout de lectura REST/SOAP (default 15000 ms) |

Los endpoints de sincronización responden HTTP `202 Accepted`. El campo
`solicitudId` también se envía como `JMSCorrelationID`, para poder correlacionar
la solicitud REST con el procesamiento y los logs del consumidor asíncrono.

Para otra OTA se reemplaza `booking` por el nombre del canal en minúsculas. El contrato REST
saliente utiliza `PUT {urlBase}/inventario` y `PUT {urlBase}/tarifas`. El cliente SOAP espera
`PmsLegacyService` en el namespace `http://pms.stayhub.com/legacy`; esos nombres se ajustan al
WSDL real cuando el proveedor del PMS lo entregue.
