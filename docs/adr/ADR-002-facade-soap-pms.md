# ADR-002: Un único servicio SOAP como fachada para el PMS legado

- **Estado:** Aceptada
- **Fecha:** 12/11/2026
- **Componente:** IntegracionPms
- **Patrón:** Facade (GoF)

## Contexto

El PMS (Property Management System) de recepción del hotel es un sistema **legado**
que sólo sabe integrarse por SOAP/WSDL. En el check-in, el recepcionista tiene al
huésped adelante y necesita la respuesta en el momento, así que la integración es
**sincrónica** (request/response) y no por mensajería.

Lo que el PMS necesita está repartido en dos componentes:

- **ServicioDeReservas:** consultar una reserva, listar las llegadas del día y cancelar.
- **ServicioDeInventarioYTarifas:** consultar disponibilidad y tarifas.

Exponer cada componente por SOAP por separado obligaría al PMS a conocer la estructura
interna de StayHub y metería detalles de SOAP (anotaciones JAX-WS, tipos XML, SOAP
Faults) dentro de componentes que hoy sólo hablan Java y REST.

## Decisión

Se expone **un único servicio SOAP**, `IntegracionPmsWebService`
(`integracionpms/api/IntegracionPmsWebService.java`), que actúa como **fachada**:

- Publica un solo WSDL en `/StayHub/soap/integracion-pms?wsdl`, generado code-first a
  partir de las anotaciones JAX-WS, con estilo *document/literal wrapped* (el que
  recomienda WS-I Basic Profile).
- Ofrece cinco operaciones: `consultarReserva`, `listarLlegadas`, `cancelarReserva`,
  `consultarDisponibilidad` y `consultarTarifas`.
- **No tiene lógica de negocio propia.** Traduce tipos (DTOs XML de `integracionpms/dto`,
  con `IntegracionPmsMapper`) y errores (`IntegracionPmsFault`), y delega en
  `ServicioDeReservas` y `ServicioDeInventarioYTarifas`.
- Los errores se devuelven como **SOAP Fault** con un código de negocio
  (`CodigoErrorIntegracionPms`), separando los Fault de negocio (reserva inexistente,
  transición inválida) de los técnicos. Nunca se filtra una excepción interna al cliente.
- El PMS se autentica con certificado de cliente, y el XML de entrada se procesa con
  protección contra XXE (DTD y entidades externas deshabilitadas).

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Un servicio SOAP por componente (uno para Reservas, otro para Inventario) | El PMS tendría que consumir dos WSDL y conocer cómo está dividido StayHub por dentro. Cada componente quedaría acoplado a SOAP. |
| Anotar directamente `ServicioDeReservasImpl` con `@WebService` | Mezcla la capa de presentación (SOAP) con la de negocio, rompe la arquitectura en capas y expone los tipos internos en el WSDL: cualquier cambio interno rompería el contrato con el PMS. |
| Exponer al PMS la API REST existente | La restricción del dominio es que el PMS legado **sólo** entiende SOAP/WSDL. |

## Consecuencias

**Positivas**
- El PMS ve un contrato estable y simple (un WSDL, cinco operaciones), independiente
  de cómo estén organizados los componentes internos.
- Los componentes de negocio no dependen de SOAP: pueden cambiar mientras la fachada
  mantenga el contrato.
- Los errores del PMS quedan centralizados en un solo lugar (Fault de negocio vs. técnico).
- Se prueba aislada (`IntegracionPmsWebServiceTest`), y el WSDL publicado se valida
  como contrato en el CI (`contracts/verify-wsdl.mjs`).

**Negativas / costos**
- Si el PMS necesitara una operación nueva, hay que sumarla a la fachada y al WSDL,
  aunque la lógica ya exista en un componente.
- La fachada duplica algunos DTOs (versión XML de reserva, disponibilidad y tarifa), y
  el mapper hay que mantenerlo.
