# Colecciones de Postman — StayHub

Base URL usada por todas las colecciones:

```
http://localhost:8080/StayHub/api
```

## Colecciones disponibles

| Archivo | Componente |
| --- | --- |
| `StayHub-ServicioDeHoteles.postman_collection.json` | ServicioDeHoteles |
| `StayHub-InventarioYTarifas.postman_collection.json` | ServicioDeInventarioYTarifas |
| `StayHub-ServicioDeReservas.postman_collection.json` | ServicioDeReservas (incluye Carrito de reserva y la seguridad por dueño) |
| `Stayhub pagos.postman collection.json` | ServicioDePagos |
| `Stayhub usuarios.postman.collection.json` | ServicioDeUsuarios |
| `StayHub-IntegracionPmsSOAP.postman_collection.json` | Servicio SOAP expuesto al PMS legado (base `http://localhost:8080/StayHub/soap/integracion-pms`, no usa `/api`) |

## Configuración necesaria para probar Canales Externos (sincronización OTA/PMS)

Antes de correr las requests de `/canales-externos/otas/.../sincronizaciones` y `/canales-externos/pms/sincronizaciones`, agregar estas system properties desde la CLI de WildFly:

```
/system-property=stayhub.ota.booking.url:add(value="https://c045a53c-5350-4944-87b6-b65b970cd586.mock.pstmn.io")
/system-property=stayhub.pms.wsdl:add(value="http://localhost:9091/PmsLegacyService?wsdl")
:reload
```

- La URL de OTA apunta a un mock server de Postman (canal `booking`).
- La URL de PMS asume un servicio SOAP corriendo localmente en `localhost:9091`.
- Sin estas propiedades, las operaciones dependientes responden `503 DEPENDENCIA_NO_DISPONIBLE`.

## Datos base recomendados antes de probar

1. `GET /saludo` — smoke test, debe responder `200`.
2. `POST /inventario-tarifas/cargas` con hotel, tipo de habitación, rango de fechas, unidades, precio y moneda — necesario para tener disponibilidad cargada antes de probar reservas o canales externos.
3. `GET /inventario-tarifas/disponibilidad` — verificar el cupo inicial.

## Servicio SOAP para el PMS legado

La colección `StayHub-IntegracionPmsSOAP` manda sobres SOAP 1.1 (`POST`, `Content-Type: text/xml`) a
`http://localhost:8080/StayHub/soap/integracion-pms`. No necesita login: lo consume otro sistema.

1. Correr `00 - Ver WSDL` para confirmar que el servicio quedó publicado.
2. Crear una reserva con la colección de ServicioDeReservas y poner su id en la variable `reservaId`.
3. Correr el resto en orden; `07 - cancelarReserva` va al final porque deja la reserva CANCELADA.

Los casos `02` y `06` muestran el SOAP Fault (HTTP 500 con `soap:Fault` y el código en `detail`).
