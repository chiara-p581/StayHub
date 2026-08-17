# ServicioDeCanalesExternos

Componente de integración de StayHub. Expone webhooks/endpoints REST para OTAs, publica
inventario y tarifas mediante JAX-RS Client y sincroniza un PMS legado mediante JAX-WS/SOAP.
No calcula disponibilidad, tarifas ni reglas de reservas.

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

## Configuración

Puede suministrarse como propiedad de sistema de WildFly o variable de entorno:

| Propiedad | Variable equivalente | Uso |
|---|---|---|
| `stayhub.ota.booking.url` | `STAYHUB_OTA_BOOKING_URL` | URL base de Booking |
| `stayhub.ota.booking.token` | `STAYHUB_OTA_BOOKING_TOKEN` | Bearer token opcional |
| `stayhub.pms.wsdl` | `STAYHUB_PMS_WSDL` | URL del WSDL del PMS |

Para otra OTA se reemplaza `booking` por el nombre del canal en minúsculas. El contrato REST
saliente utiliza `PUT {urlBase}/inventario` y `PUT {urlBase}/tarifas`. El cliente SOAP espera
`PmsLegacyService` en el namespace `http://pms.stayhub.com/legacy`; esos nombres se ajustan al
WSDL real cuando el proveedor del PMS lo entregue.
