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

Las consultas y sincronizaciones iniciadas desde la interfaz requieren una sesión
StayHub con rol `ADMIN`. Los webhooks de reservas OTA aceptan esa sesión administrativa
con una firma HMAC por proveedor enviada en `X-StayHub-Signature`, junto con
`X-StayHub-Timestamp` y `X-StayHub-Nonce`.

La clave se configura fuera del repositorio mediante la propiedad de sistema
`stayhub.canales.<canal>.webhook-secret` o su variable de entorno equivalente. No debe incluirse
en código, archivos versionados ni URLs.

## Configuración

Puede suministrarse como propiedad de sistema de WildFly o variable de entorno:

| Propiedad | Variable equivalente | Uso |
|---|---|---|
| `stayhub.ota.booking.url` | `STAYHUB_OTA_BOOKING_URL` | URL base de Booking |
| `stayhub.ota.booking.token` | `STAYHUB_OTA_BOOKING_TOKEN` | Bearer token opcional |
| `stayhub.pms.wsdl` | `STAYHUB_PMS_WSDL` | URL del WSDL del PMS |
| `stayhub.canales.<canal>.webhook-secret` | `STAYHUB_CANALES_<CANAL>_WEBHOOK_SECRET` | Secreto HMAC individual para webhooks OTA |
| `stayhub.canales.hosts-permitidos` | `STAYHUB_CANALES_HOSTS_PERMITIDOS` | Allowlist exacta de hosts OTA/PMS |
| `stayhub.canales.timeout.conexion.ms` | `STAYHUB_CANALES_TIMEOUT_CONEXION_MS` | Timeout de conexión REST/SOAP (default/máximo 750 ms) |
| `stayhub.canales.timeout.lectura.ms` | `STAYHUB_CANALES_TIMEOUT_LECTURA_MS` | Timeout de lectura REST/SOAP (default/máximo 1500 ms) |

Los endpoints de sincronización responden HTTP `202 Accepted`. El campo
`solicitudId` también se envía como `JMSCorrelationID`, para poder correlacionar
la solicitud REST con el procesamiento y los logs del consumidor asíncrono.

Para otra OTA se reemplaza `booking` por el nombre del canal en minúsculas. El contrato REST
saliente utiliza `PUT {urlBase}/inventario` y `PUT {urlBase}/tarifas`. El cliente SOAP espera
`PmsLegacyService` en el namespace `http://pms.stayhub.com/legacy`; esos nombres se ajustan al
WSDL real cuando el proveedor del PMS lo entregue.

Los envíos REST y SOAP son idempotentes y admiten un único reintento inmediato ante
errores de comunicación. El cliente SOAP deriva el endpoint desde la URL configurada y
no descarga el WSDL en cada mensaje, evitando una conexión inicial sin timeout. Dos
intentos agotados generan un error controlado para que Artemis pueda reprocesar el mensaje.
