# KAN-99 — Informe de OWASP ZAP

## Alcance ejecutado localmente

Fecha: 9 de noviembre de 2026.

Objetivo analizado: `https://localhost:8443/StayHub` sobre WildFly y la base
Aiven utilizada por el equipo. Para limitar el impacto se creó un único huésped
sintético (`zap.guest.1794265643@stayhub.local`) y se recorrieron únicamente:

- página de inicio;
- configuración de la cuenta;
- consulta de reservas propias;
- consulta de pagos propios.

No se ejecutaron cargas activas, pagos, reservas, cancelaciones, modificaciones,
carga de datos demo ni operaciones administrativas sobre Aiven. El reporte JSON
local se genera en `ci-evidence/zap/local-authenticated-passive.json`, una ruta
ignorada por Git porque puede contener URLs y evidencia específica del entorno.

## Resultado del rastreo pasivo autenticado

| Severidad | Cantidad | Tratamiento |
|---|---:|---|
| High | 0 | Cumple el criterio de aceptación |
| Medium | 1 | Riesgo justificado y registrado |
| Low | 1 | Falso positivo del dato sintético |
| Informativa | 2 | Registradas para revisión |

### Medium — CSP `style-src 'unsafe-inline'`

ZAP detectó que la política CSP permite estilos inline. La excepción existe
porque el frontend actual todavía contiene bloques `<style>`, atributos
`style` y ajustes visuales dinámicos. Se acepta temporalmente para esta entrega
por las siguientes defensas compensatorias:

- `script-src` permanece limitado a `'self'` y no permite scripts inline;
- `object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'` y
  `form-action 'self'` reducen superficies de inyección y navegación;
- el contenido no confiable se inserta mediante `textContent` o se sanea con la
  copia local de DOMPurify;
- scripts, fuentes e imágenes ejecutables no se cargan desde CDN de terceros.

Riesgo residual: una inyección HTML que alcance un atributo de estilo podría
alterar la presentación. La remediación definitiva requiere migrar todos los
estilos inline a hojas CSS versionadas y retirar `'unsafe-inline'` de
`SeguridadHttpFilter`. Este cambio se mantiene separado porque afecta numerosas
pantallas y necesita regresión visual completa.

### Low — Timestamp Disclosure

El valor señalado por ZAP (`1794265643`) pertenece al correo generado para el
usuario sintético y no es un timestamp interno revelado por StayHub. Se
clasifica como falso positivo reproducible.

### Informativas

- `Modern Web Application`: clasificación esperada para el frontend dinámico.
- `Re-examine Cache-control Directives`: los endpoints autenticados deben
  conservar `no-store`; los recursos estáticos pueden utilizar caché.

## Escaneo activo aislado

El workflow `StayHub CI` incorpora el job `OWASP ZAP autenticado`. Este job usa
una base PostgreSQL descartable y ejecuta el análisis como Huésped y como
Administrador con MFA. Publica reportes HTML, JSON y Markdown y falla si existe
alguna alerta High. Esta es la ejecución válida para cargas activas porque no
afecta los datos compartidos de Aiven.
