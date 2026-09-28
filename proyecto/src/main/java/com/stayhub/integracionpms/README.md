# Integración SOAP con el PMS legado

Servicio web **SOAP** que StayHub expone al **PMS (Property Management System) legado
del hotel**. Es la integración síncrona SOAP que pide el TP (Entrega 4): un servicio
con su **WSDL**, justificado como conexión con un sistema externo legado.

## Por qué SOAP y por qué sincrónico

- **SOAP**: el PMS de recepción es un sistema legado que solo sabe integrarse
  mediante SOAP/WSDL. No le podemos pedir que consuma nuestra API REST.
- **Sincrónico**: el PMS consulta a StayHub en el momento del check-in. El
  recepcionista tiene al huésped adelante y no puede seguir sin la respuesta
  (criterio de la Clase 9: "el usuario está esperando en pantalla" y "es una
  consulta rápida").

## Dos sentidos de la integración con el PMS

| Sentido | Quién es proveedor | Dónde está | Modo |
| --- | --- | --- | --- |
| PMS → StayHub (consultas de recepción) | **StayHub** | este paquete (`IntegracionPmsWebService`) | SOAP síncrono |
| StayHub → PMS (sincronizar inventario y tarifas) | **PMS** | `canalesexternos/client/pms` (cliente) y `pms-legado-mock/` (PMS simulado) | JMS asíncrono + SOAP |

En el segundo sentido StayHub no espera al PMS: la sincronización se encola en JMS y
un MDB llama al PMS por SOAP. Si el PMS está lento o caído, la transacción del
consumidor se revierte y el broker reintenta, sin bloquear la API.

## Contrato (WSDL)

Enfoque **code-first**: la clase anotada es el contrato y WildFly genera el WSDL en

```
http://localhost:8080/StayHub/soap/integracion-pms?wsdl
```

- Namespace: `http://soap.stayhub.com/integracion-pms`
- `service` `IntegracionPmsService` → `port` `IntegracionPmsPort` → `portType` `IntegracionPmsPortType`
- SOAP 1.1, estilo **document/literal wrapped** (WS-I Basic Profile)

| Operación | Entrada | Salida | Delega en |
| --- | --- | --- | --- |
| `consultarReserva` | `reservaId` | `reserva` | ServicioDeReservas |
| `listarLlegadas` | `hotelId`, `fecha` | `reserva*` (CONFIRMADAS con check-in ese día) | ServicioDeReservas |
| `cancelarReserva` | `reservaId` | `reserva` | ServicioDeReservas |
| `consultarDisponibilidad` | `hotelId`, `desde`, `hasta` | `disponibilidad*` | ServicioDeInventarioYTarifas |
| `consultarTarifas` | `hotelId`, `desde`, `hasta` | `tarifa*` | ServicioDeInventarioYTarifas |

Las fechas viajan como texto `AAAA-MM-DD`.

### SOAP Fault

Todas las operaciones declaran `IntegracionPmsFault`. En el WSDL aparece como
`<wsdl:fault>` y en la respuesta como `soap:Fault` con este detalle:

```xml
<detail>
  <ns2:errorIntegracionPms xmlns:ns2="http://soap.stayhub.com/integracion-pms">
    <codigo>RESERVA_NO_ENCONTRADA</codigo>
    <mensaje>No existe la reserva 999999</mensaje>
  </ns2:errorIntegracionPms>
</detail>
```

Códigos: `SOLICITUD_INVALIDA`, `RESERVA_NO_ENCONTRADA`, `TRANSICION_DE_ESTADO_INVALIDA`,
`SIN_DISPONIBILIDAD`, `DEPENDENCIA_NO_DISPONIBLE`, `ERROR_INTERNO`.

## Patrones

- **Facade**: el servicio no tiene lógica de negocio; expone en un único contrato
  operaciones de dos componentes (Reservas e Inventario) y oculta su estructura interna.
- **DTO / Mapper**: `IntegracionPmsMapper` traduce los records internos a clases JAXB.
  Así el WSDL no cambia si cambian los DTOs internos, y se evita que JAXB tenga que
  serializar `record` o `LocalDate`, que no soporta.

## Estructura

```
com.stayhub.integracionpms
├── api/        IntegracionPmsWebService (@WebService) + IntegracionPmsMapper
├── dto/        ReservaPmsDTO, DisponibilidadPmsDTO, TarifaPmsDTO (tipos XML del WSDL)
└── exception/  IntegracionPmsFault (@WebFault), ErrorIntegracionPms (fault bean), códigos
```

La URL se mapea en `WEB-INF/web.xml`. El endpoint no pasa por `AutenticacionFilter`,
porque ese filtro es de JAX-RS; lo consume otro sistema, no un usuario con sesión.

## Probarlo

1. Desplegar StayHub en WildFly y abrir el WSDL en el navegador.
2. Colección `postman/StayHub-IntegracionPmsSOAP.postman_collection.json`, o importar
   el WSDL en SoapUI.
3. Tests unitarios: `IntegracionPmsWebServiceTest`.

## Mejora posible

Autenticar al PMS con WS-Security (UsernameToken en el `soap:Header`) o con una
clave técnica, igual que los webhooks de OTAs usan `X-StayHub-Channel-Key`.
