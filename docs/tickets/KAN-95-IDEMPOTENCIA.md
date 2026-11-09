# KAN-95 — Idempotency-Key en POST críticos

Revalidado después de integrar development: los cuatro casos HTTP ampliados
aprobaron, junto con la conservación de la clave al reautenticar en la interfaz.
Ver [integración del 9/11](INTEGRACION-DEVELOPMENT-2026-11-09.md).

Reservas, pagos y pagos por lote aceptan `Idempotency-Key`. La clave se valida
y se asocia al usuario; las restricciones persistentes y bloqueos impiden
duplicar el efecto. En pagos, si también se manda `claveIdempotencia` en JSON,
ambos valores deben coincidir. La migración V4 agrega la clave de reserva y
su índice único. Se actualizó el cliente web y los contratos HTTP.

La prueba real de compra repite el pago y exige el mismo identificador y una
reserva confirmada. La prueba de cancelación repite el alta de reserva y exige
el mismo identificador y una sola reducción de cupo; luego verifica la
restitución del inventario. Ambos flujos pasaron en WildFly local.

Aceptación ampliada del 9/11: `e2e/tests/tickets-idempotency-acceptance.spec.cjs`
pasó cuatro casos reales: reserva concurrente, pago concurrente y aislamiento
por usuario/operación, rechazo de reutilización para otro lote y lote concurrente.
Se comparan cuerpos JSON completos. Se corrigió un defecto de reutilización:
si la clave de un pago existente no coincide con reservas, monto y moneda del
pedido, responde 409. El bloqueo por usuario precede los bloqueos de reservas.

Seguridad: la clave no reemplaza sesión, CSRF, autorización de propiedad ni
validación del precio en servidor. El dominio `StayHubSecurity` permite al
contenedor usar la identidad autenticada por StayHub de manera aislada.
Ver [decisión del dominio](../SEGURIDAD_DOMINIO_STAYHUB.md).
