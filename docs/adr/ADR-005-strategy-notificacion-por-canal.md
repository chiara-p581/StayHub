# ADR-005: Una estrategia de envío por canal de notificación

- **Estado:** Aceptada
- **Fecha:** 12/11/2026
- **Componente:** ServicioDeNotificaciones
- **Patrón:** Strategy (GoF) / Polimorfismo (GRASP)

## Contexto

StayHub avisa a los usuarios en varios momentos (pago aprobado, reserva confirmada,
registro, recuperación de contraseña, reubicación por overbooking), y cada aviso puede
salir por distintos **canales**: EMAIL, SMS o PUSH. Cada canal se envía de forma
totalmente distinta:

- **EMAIL:** SMTP con autenticación y STARTTLS, plantilla HTML, límite de envíos por
  destinatario y configuración externa (fuera del repositorio).
- **SMS y PUSH:** hoy simulados (registran el procesamiento sin proveedor externo). En
  el futuro podrían conectarse a proveedores reales.

El servicio recibe una `SolicitudNotificacionDTO` con el canal preferido y tiene que
despachar al mecanismo correcto. La consigna pide además que el sistema pueda sumar
canales sin rehacer lo existente.

## Decisión

Se define la interfaz **`NotificadorCanal`** (`notificaciones/client/NotificadorCanal.java`),
con una implementación por canal:

| Estrategia | Canal |
| --- | --- |
| `EmailNotificador` | EMAIL (SMTP real cuando está configurado) |
| `SmsNotificador` | SMS (simulado) |
| `PushNotificador` | PUSH (simulado) |

- Cada estrategia declara qué canal atiende (`canal()`) y cómo enviar (`enviar(...)`).
- `ServicioDeNotificacionesImpl` recibe **todas** las estrategias disponibles mediante
  CDI (`Instance<NotificadorCanal>`) y elige la que atiende el canal pedido
  (`notificadorPara(canal)`), sin `switch` sobre el canal. Si ninguna estrategia atiende
  el canal, responde con el error de negocio `CANAL_NO_SOPORTADO`.
- Los consumidores JMS (pagos, usuarios, overbooking) arman la solicitud y no saben qué
  canal se usa ni cómo se envía. El envío de mails pasa además por
  `EnvioCorreoIdempotente`, para que un mensaje reentregado no mande dos veces el mismo correo.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| `switch (canal)` dentro del servicio, con el código de cada canal | Cada canal nuevo obliga a modificar el servicio (viola abierto/cerrado), y el servicio crece con detalles de SMTP, SMS y push mezclados. |
| Una Factory que construya el notificador según el canal | Agrega una clase más que sólo repite lo que CDI ya resuelve: el contenedor descubre e inyecta todas las implementaciones. Con `Instance<…>` la selección queda en una línea. |
| Un servicio de notificación separado por canal, invocado por cada consumidor | Cada consumidor (pagos, usuarios, overbooking) tendría que conocer todos los canales y decidir cuál usar. Se duplica la lógica de selección. |

## Consecuencias

**Positivas**
- Para sumar un canal (por ejemplo, WhatsApp) alcanza con una clase nueva que implemente
  `NotificadorCanal`. No se toca el servicio ni los consumidores (principio
  abierto/cerrado).
- Cada canal se prueba por separado, y la selección se prueba con estrategias simuladas
  (`ServicioDeNotificacionesCanalesTest`).
- Los canales simulados (SMS, PUSH) y el real (EMAIL) conviven con la misma interfaz:
  pasar SMS a un proveedor real sólo cambia su estrategia.

**Negativas / costos**
- Si dos implementaciones declaran el mismo canal, la selección toma la primera que
  encuentra. La convención es una estrategia por canal.
- Los errores de cada canal son distintos (SMTP, cuotas, proveedor). Cada estrategia
  tiene que traducirlos a excepciones uniformes para que los consumidores JMS decidan
  si reintentar o mandar a la DLQ.
