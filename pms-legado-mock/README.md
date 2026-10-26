# PMS legado (simulado)

Simulador del **PMS (Property Management System) legado del hotel**, el sistema
externo con el que StayHub se integra por SOAP. Como el PMS real no existe,
lo simulamos con un servicio JAX-WS (Clase 9, slide 39).

Es un proyecto **aparte** de StayHub: corre en su propia JVM y puerto, como
correría el sistema real del hotel.

## Levantarlo

Requiere Java 17 y Maven.

```bash
cd pms-legado-mock
mvn compile exec:java
```

Queda escuchando en `http://localhost:9091/PmsLegacyService` y el contrato se
ve en <http://localhost:9091/PmsLegacyService?wsdl>.

## Conectarlo con StayHub

StayHub (ServicioDeCanalesExternos → `PmsLegacyClientImpl`) lee la URL del WSDL
de la system property `stayhub.pms.wsdl`. Desde la CLI de WildFly:

```
/system-property=stayhub.pms.wsdl:add(value="http://localhost:9091/PmsLegacyService?wsdl")
:reload
```

Después, `POST /StayHub/api/canales-externos/pms/sincronizaciones?hotelId=1&desde=...&hasta=...`
encola la sincronización; el consumidor JMS llama a este PMS por SOAP y en la
consola del mock se ve lo que recibió.

## Simular un PMS lento (desafío del timeout)

```bash
mvn compile exec:java -Dpms.demora.ms=20000
```

El cliente de StayHub corta a los 15 s (`stayhub.canales.timeout.lectura.ms`),
el consumidor JMS revierte su transacción y el broker reintenta la entrega más
tarde: la API de StayHub nunca queda bloqueada esperando al PMS.
