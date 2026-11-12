# ADR-003: Puertos propios por consumidor y adaptadores hacia otros componentes y sistemas externos

- **Estado:** Aceptada
- **Fecha:** 12/11/2026
- **Componentes:** ServicioDeCanalesExternos, ServicioDeInventarioYTarifas, ServicioDeReservas
- **Patrón:** Adapter (GoF), aplicado como «puertos y adaptadores»

## Contexto

StayHub tiene dos tipos de dependencias con interfaces que no coinciden con lo que
cada componente necesita:

1. **Sistemas externos con protocolos distintos.** Las OTAs (Booking, Expedia, Airbnb,
   Despegar) se integran por REST/JSON, y el PMS legado por SOAP. Si
   ServicioDeCanalesExternos llamara directamente a JAX-RS o JAX-WS, la lógica de
   sincronización quedaría mezclada con detalles de HTTP, timeouts, URLs y XML.
2. **Componentes internos que evolucionan por separado.** ServicioDeCanalesExternos y
   ServicioDeOverbooking necesitan leer disponibilidad y tarifas, pero cada uno con sus
   propios DTOs. Si dependieran directamente de los DTOs de
   ServicioDeInventarioYTarifas, cualquier cambio en Inventario los rompería. Además, Java
   no permite que una misma clase implemente dos interfaces con métodos de igual firma
   y distinto tipo de retorno.

## Decisión

Cada componente define el **puerto** (interfaz) que necesita, con sus propios tipos, y
un **adaptador** implementa ese puerto sobre el sistema o componente real.

**Hacia componentes internos**

| Puerto (lo define el consumidor) | Adaptador | Adapta a |
| --- | --- | --- |
| `canalesexternos/contrato/interno/ServicioDeInventarioYTarifasPort` | `inventarioytarifas/service/AdaptadorPortCanalesExternos` | `ServicioDeInventarioYTarifas` |
| `ServicioDeInventarioYTarifasPort` de Overbooking (mismo nombre, otro paquete y otros DTOs) | `inventarioytarifas/service/AdaptadorPortOverbooking` | `ServicioDeInventarioYTarifas` |

Los adaptadores sólo traducen DTOs. No agregan reglas de negocio.

Reservas también define su puerto, `reservas/contrato/GestionDeDisponibilidadPort`
(pedir, confirmar y liberar holds de cupo). Ahí no hace falta adaptador:
`ServicioDeInventarioYTarifasImpl` lo implementa directamente, porque los tipos del
puerto no chocan con los suyos. La regla es la misma: el consumidor define el contrato.

**Hacia sistemas externos**

| Puerto | Adaptador | Protocolo |
| --- | --- | --- |
| `canalesexternos/client/ota/OtaRestClient` | `OtaRestClientImpl` | REST/JSON (JAX-RS Client), timeouts de 750 ms de conexión y 1,5 s de lectura, token Bearer opcional por OTA |
| `canalesexternos/client/pms/PmsLegacyClient` | `PmsLegacyClientImpl` | SOAP (JAX-WS) contra el WSDL del PMS |

`ProcesadorSincronizacion` trabaja sólo contra estos puertos. Sobre ellos aplica un
reintento acotado y, si el sistema externo no responde, un fallback controlado: el
mensaje JMS puede reprocesarse y termina en la DLQ si falla todas las veces.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Que los consumidores usen directamente la interfaz y los DTOs de `ServicioDeInventarioYTarifas` | Acopla cada consumidor a los tipos internos de Inventario. Además, `ServicioDeInventarioYTarifasImpl` no puede implementar a la vez los puertos de Canales y de Overbooking (misma firma, distinto retorno). |
| Llamar a JAX-RS / JAX-WS directamente desde el servicio de negocio | Mezcla negocio con infraestructura, y no permite probar la sincronización sin levantar las OTAs o el PMS. |
| Un DTO compartido para todos los componentes | Crea un «modelo común» que nadie puede cambiar sin coordinar con todos, justo lo que la orientación a componentes busca evitar. |

## Consecuencias

**Positivas**
- Cada componente depende de una interfaz que él mismo define (inversión de
  dependencias). Inventario puede cambiar sus DTOs sin romper a Canales ni a Overbooking.
- Los sistemas externos se pueden reemplazar por mocks (`ota-mock`, `pms-mock`) o por
  dobles de prueba sin tocar la lógica de negocio. Así se prueban
  `ServicioDeCanalesExternosImplTest` y `ProcesadorSincronizacionTest`.
- Cambiar una OTA real o el protocolo del PMS impacta sólo a su adaptador.
- Timeouts, autenticación y manejo de errores de red quedan en un solo lugar por
  sistema externo.

**Negativas / costos**
- Más clases e interfaces, y mapeos de DTOs que hay que mantener (los cubre
  `InventarioTarifasMapperYAdaptadoresTest`).
- Hay que decidir conscientemente qué consumidor es dueño de cada puerto. Si no, se
  duplican interfaces casi iguales.
