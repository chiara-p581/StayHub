# Colecciones de Postman — StayHub

Base URL recomendada para Docker Compose:

```
https://localhost:8443/StayHub/api
```

## Colecciones disponibles

| Archivo | Componente |
| --- | --- |
| `StayHub-ServicioDeHoteles.postman_collection.json` | ServicioDeHoteles |
| `StayHub-InventarioYTarifas.postman_collection.json` | ServicioDeInventarioYTarifas |
| `StayHub-ServicioDeReservas.postman_collection.json` | ServicioDeReservas (incluye Carrito de reserva y la seguridad por dueño) |
| `StayHub-ServicioDePagos.postman_collection.json` | ServicioDePagos |
| `StayHub-ServicioDeUsuarios.postman_collection.json` | ServicioDeUsuarios |
| `StayHub-ServicioDeNotificaciones.postman_collection.json` | ServicioDeNotificaciones |
| `StayHub-ServicioDeOverbooking.postman_collection.json` | ServicioDeOverbooking |
| `StayHub-ServicioDeCanalesExternos.postman_collection.json` | ServicioDeCanalesExternos y catálogo multicanal |
| `StayHub-IntegracionPmsSOAP.postman_collection.json` | Servicio SOAP expuesto al PMS legado (base `https://localhost:8443/StayHub/soap/integracion-pms`, no usa `/api`) |

Todas las colecciones REST incluyen la carpeta reproducible `CI - Contrato mínimo`. El pipeline la ejecuta
con Newman en cada colección, ejecuta además los recorridos ampliados de Canales Externos y SOAP, y publica
los reportes JUnit. El contrato OpenAPI se genera y valida con `npm --prefix contracts run verify`; cualquier
endpoint JAX-RS nuevo queda incorporado automáticamente y se conserva como evidencia de CI. Esa verificación
también exige que cada request Postman tenga al menos una aserción.

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
`https://localhost:8443/StayHub/soap/integracion-pms`. **No usa login de usuario, pero sí exige
TLS mutuo**: el PMS entrante se identifica con un certificado de cliente, no con sesión ni token.

### Requisito previo: certificado de cliente del PMS

`/soap/*` corre siempre sobre HTTPS y, además, exige que quien llama presente un certificado cuya
huella SHA-256 coincida con `stayhub.pms.certificado-sha256` y cuyos hoteles estén en
`stayhub.pms.hoteles` (ver `SeguridadHttpFilter.autenticarPms`). Sin esto, el request cae con
`401`/`403` antes de llegar al WSDL.

1. Generar (una sola vez por entorno) el certificado y vincularlo a los hoteles autorizados:
   ```sh
   python3 config/configurar-seguridad-local.py \
     --wildfly-home /ruta/a/wildfly \
     --openssl /ruta/a/openssl \
     --hoteles-pms 1
   ```
   Esto crea `.local-security/certificados/pms.p12` (certificado de cliente) y `ca.pem` (CA local),
   y carga en WildFly la huella y la lista de hoteles autorizados. Después hace falta `:reload`.
2. En Postman: **Settings → Certificates → Add Certificate**, host `localhost:8443`, archivo
   `.local-security/certificados/pms.p12` (contraseña en `.local-security/secretos.json`,
   clave `tls_store_password`).
3. Como el certificado del servidor es de una CA local y no pública, también hay que decirle a
   Postman que confíe en `.local-security/certificados/ca.pem`, o desactivar
   "SSL certificate verification" en Settings (solo para este entorno local de desarrollo).

Con el certificado configurado:

1. Correr `00 - Ver WSDL` para confirmar que el servicio quedó publicado.
2. Crear una reserva con la colección de ServicioDeReservas y poner su id en la variable `reservaId`.
3. Correr el resto en orden; `07 - cancelarReserva` va al final porque deja la reserva CANCELADA.

Los casos `02` y `06` muestran el SOAP Fault (HTTP 500 con `soap:Fault` y el código en `detail`).

En Docker Compose el certificado PMS se genera dentro del contenedor y el pipeline lo usa únicamente desde
archivos temporales que se eliminan al terminar. La clave privada nunca se publica como artefacto de CI.
