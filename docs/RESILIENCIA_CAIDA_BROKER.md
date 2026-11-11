# Recuperación ante caída del broker JMS (KAN-91)

Responsable: Magali Alfie · HU principal: KAN-63 · Referencia: *Criterios de Testing y
Aseguramiento de Calidad — TP Obligatorio*, sección 6 (Eje C), fila «Se apaga el broker
durante la demo y se vuelve a levantar».

## 1. Contexto

StayHub no tiene un broker separado: usa el **ActiveMQ Artemis embebido en WildFly**
(perfil `standalone-full`). Por eso «apagar el broker» equivale a detener el contenedor
`app`. La prueba lo hace con `docker compose kill app` (SIGKILL), que es el peor caso
porque simula una caída abrupta, sin apagado ordenado.

Flujos con mensajería que cubre la prueba:

| Flujo | Destino | Garantía |
| --- | --- | --- |
| Sincronización con OTAs/PMS | cola `CanalesExternosSincronizaciones` | Mensajes persistentes; el consumidor (MDB) vuelve a conectarse solo al reiniciar. |
| Pago aprobado → confirmación de reserva → mail | tópico `PagosEventos` (suscripción durable) | Se publica con `java:/JmsXA` en la **misma transacción JTA** que aprueba el pago y confirma la reserva: se confirma todo o nada. |

Persistencia del broker: Artemis guarda los mensajes persistentes en su journal
(`standalone/data/activemq`), con escritura sincrónica antes de confirmar el envío.
Desde KAN-91, el journal y el registro de transacciones (`standalone/data/tx-object-store`)
se montan en volúmenes de Docker (`wildfly_messaging`, `wildfly_tx`). Así sobreviven
tanto a un reinicio como a que se recree el contenedor (`docker compose down` sin `-v`).

## 2. Criterios de aceptación y cómo se verifican

| Criterio | Verificación automatizada |
| --- | --- |
| No se pierden mensajes persistentes. | Se detiene el consumo del MDB (`stop-delivery`) y se encolan 5 sincronizaciones. Se comprueba que el mock no recibió nada, así que los mensajes están sólo en el broker. Se mata el broker. Al volver, el mock OTA recibe las 5 y la cola queda en 0. |
| El sistema se recupera sin intervención sobre los datos. | Después de `docker compose start app` no se ejecuta ningún comando de gestión, SQL ni reencolado. El MDB retoma el consumo solo y una sincronización nueva se procesa normalmente. |
| La operación del usuario no queda inconsistente. | Se disparan 4 pagos y se mata el broker 50 ms después, con los pagos en curso. Al volver se verifica en PostgreSQL que una reserva está `CONFIRMADA` si y sólo si tiene un pago `APROBADO`, que no hay pagos `PENDIENTE`, que no hay pagos duplicados y que no hay estados inesperados. Después el usuario reintenta con la misma `claveIdempotencia`: todas las reservas quedan confirmadas con exactamente un pago cada una. |

## 3. Cómo ejecutarla

Automatizada (también corre en el job `integration-smoke` del CI):

```sh
sh ./start-local.sh                      # entorno completo
cd e2e && npm ci
STAYHUB_DOCKER_VERIFY=true npx playwright test tests/resiliencia-broker.spec.cjs
```

Archivo: `e2e/tests/resiliencia-broker.spec.cjs`. Las anotaciones del reporte de
Playwright muestran cuántos mensajes había en cola antes de la caída y qué respuesta
obtuvo cada pago en vuelo (201 o «sin respuesta»).

Versión manual para la demo en vivo:

1. Con el entorno levantado, iniciar sesión como admin y pedir varias sincronizaciones
   (`POST /api/canales-externos/otas/EXPEDIA/sincronizaciones?...`, colección Postman de Canales Externos).
2. Iniciar sesión como huésped y confirmar un pago desde la UI.
3. Mientras tanto, ejecutar `docker compose kill app`.
4. Ejecutar `docker compose start app` y esperar a que `https://localhost:8443/StayHub` responda.
5. Comprobar:
   - `docker compose logs ota-mock | grep expedia`: llegaron todas las sincronizaciones.
   - En la UI la reserva está `CONFIRMADA` con su pago, o sigue `PENDIENTE` sin pago.
     Si quedó pendiente, el huésped vuelve a pagar y no se duplica el cobro.
   - Consulta de control:
     ```sh
     docker compose exec -T postgres psql -U stayhub -d stayhub -c \
       "SELECT r.id, r.estado, count(p.*) FILTER (WHERE p.estado='APROBADO') AS pagos_aprobados
        FROM reserva r LEFT JOIN pago p ON p.reserva_id = r.id
        GROUP BY r.id, r.estado ORDER BY r.id DESC LIMIT 10;"
     ```

## 4. Por qué la operación del usuario no queda a medias

`ServicioDePagosImpl.procesarPago` es un EJB con `@TransactionAttribute(REQUIRED)`. En una
sola transacción JTA guarda el pago, lo aprueba, confirma las reservas
(`ServicioDeReservas.confirmarReserva`) y publica `PagosEventos` con la connection factory
XA `java:/JmsXA`. Si el broker cae antes del commit, la transacción no se confirma: no
queda pago, la reserva sigue `PENDIENTE` y no sale ningún evento. Si cae después del
commit pero antes de que llegue la respuesta, el usuario ve un error de red. Al
reintentar con la misma `claveIdempotencia`, `buscarRepetido` devuelve el pago existente
en lugar de cobrar otra vez.

## 5. Riesgos conocidos y limitaciones (declarados según G9)

- **Cobro externo fuera de la transacción.** `pasarela.cobrar(...)` es una llamada HTTP
  dentro de la transacción. Si la caída ocurre después de que la pasarela aprobó y antes
  del commit, la base hace rollback pero el cobro externo ya ocurrió. En el entorno local
  la pasarela es un mock. Para producción, la mitigación sería conciliar usando la
  referencia `reserva-<id>`, que la pasarela recibe en cada cobro y es idempotente, o
  usar un outbox. Queda documentado como riesgo, no como defecto del alcance actual.
- **Transacción distribuida.** `PostgresDS` es un `xa-data-source` (PostgreSQL con
  `max_prepared_transactions=100`) y JMS usa `java:/JmsXA`, así que el pago y el evento
  se confirman con *two-phase commit*. Si la caída ocurre entre la preparación y el
  commit, Narayana completa o revierte la transacción al reiniciar, usando su registro
  en `tx-object-store`. Por eso ese directorio también va en un volumen.
- **Entrega al menos una vez.** Un mensaje que estaba en proceso al momento de la caída
  puede reentregarse. El consumidor de notificaciones es idempotente
  (`notificacion_email_enviada`). La sincronización OTA/PMS reenvía el estado completo,
  así que repetirla no altera el resultado.
- **Mails en el entorno local.** Si no hay SMTP configurado, los eventos de pago terminan
  en la DLQ después de los reintentos. Eso no afecta esta prueba y está cubierto por la
  fila «Mensaje inválido o que falla N veces» del mismo eje.
- **Duración.** La prueba reinicia WildFly dos veces (la caída y la limpieza posterior)
  y suma unos 3 a 4 minutos al job `integration-smoke`, que tiene un límite de 15
  minutos.

## 6. Evidencia

Ejecución en GitHub Actions, workflow `StayHub CI` (`security.yml`), job **Integración
Docker**, PR #93, run **#125**. Las tres corridas seguidas dieron el mismo resultado,
así que la prueba no es inestable.

| Corrida | Fecha | Resultado | Duración del job | Referencia |
| --- | --- | --- | --- | --- |
| 1 | 11/11/2026 | Pasada | — | Run #125, intento 1 (falló sólo *Responsive y accesibilidad* por DNS del runner, ajeno a esta prueba) |
| 2 | 11/11/2026 | Pasada | 6 min 37 s | Run #125, intento 2 (todos los jobs en verde) |
| 3 | 11/11/2026 | Pasada | 6 min 18 s | Run #125, intento 3 (todos los jobs en verde) |

Las anotaciones del reporte de Playwright (artefacto `e2e-evidence`) registran los
mensajes en cola antes de la caída y la respuesta de cada pago en vuelo.

### Defectos de la prueba corregidos durante el desarrollo

Estos fueron errores del propio test, no defectos del sistema. Se dejan registrados como
lecciones aprendidas.

- El admin demo pasó a requerir MFA: el test ahora completa el segundo factor (TOTP).
- `POST /api/reservas` y `POST /api/pagos` exigen el header `Idempotency-Key`.
- Los cortes de conexión de los pagos en vuelo se manejan en el momento, para que no
  queden como rechazos sin manejar.
- El test contaba las llamadas en una ruta equivocada del mock. `OtaRestClientImpl`
  publica en `{url}/inventario`, no en `/disponibilidad`.
- La prueba E2E crítica existente (*la sincronización JMS llega a los mocks*) valida el
  log OTA con un patrón genérico que también coincide con las llamadas a la pasarela de
  pagos. Se recomienda filtrar por ruta (`/booking/inventario`).
