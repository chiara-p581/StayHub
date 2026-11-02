# Pruebas de responsive y accesibilidad (UI)

Verifica las 19 pantallas principales de StayHub en **360, 768 y 1280 px**, en **tema claro y oscuro**, con cuatro instrumentos:

| Instrumento | Pruebas | Qué comprueba | Criterio |
|---|---|---|---|
| `tests/responsive-accesibilidad.spec.cjs` (Playwright + axe-core) | 114 | Sin desplazamiento horizontal, meta viewport que permite zoom, reglas axe WCAG 2.0/2.1/2.2 nivel A y AA, sin errores de JavaScript | 0 violaciones críticas y 0 graves |
| `tests/teclado.spec.cjs` | 57 | Recorrido con Tab: foco visible en todo control, foco no tapado por el encabezado fijo (WCAG 2.4.11) y sin trampas de teclado | 0 incumplimientos |
| `tests/contraste-controles.spec.cjs` | 38 | Límite de cada campo de formulario con contraste de al menos 3:1 (WCAG 1.4.11, que axe no mide) | 0 por debajo de 3:1 |
| `lighthouse.mjs` (Lighthouse, categoría Accesibilidad) | 76 mediciones | Puntaje por pantalla en móvil (360 px) y escritorio (1280 px), en ambos temas | Puntaje ≥ 90 en todas |

No necesita Docker, base de datos ni broker: `servidor-mock.mjs` sirve las pantallas de `proyecto/src/main/webapp`, simula la API REST y fija la sesión (rol ADMIN o HUESPED según la pantalla). Es solo un arnés de pruebas, no forma parte del producto.

## Uso

```
cd ui-accesibilidad
npm ci
npx playwright install chromium
npm test              # Playwright: 209 pruebas (axe, teclado y contraste de campos), ~1 min 20 s
npm run lighthouse    # Lighthouse: 76 mediciones, ~4,5 min con 4 procesos (LH_WORKERS)
npm run verificar     # las dos cosas
```

Variables útiles: `LH_UMBRAL` (por defecto 90), `LH_WORKERS` (por defecto 4), `UI_PORT`, `LH_PORT`. El tema oscuro se elige con `localStorage.stayhub_theme = "dark"` (Playwright) o con `TEMA=oscuro` en el servidor (Lighthouse lo hace solo).

## Reportes (carpeta `reports/`, no se versiona)

| Ruta | Contenido |
|---|---|
| `reports/playwright/` | Reporte HTML de Playwright |
| `reports/axe/<pantalla>-<ancho>-<tema>.json` | Violaciones de axe con selector y detalle de cada nodo |
| `reports/teclado/<pantalla>-<ancho>-<tema>.json` | Controles recorridos con Tab, sin indicador de foco, tapados y trampas |
| `reports/contraste-controles/<pantalla>-<tema>.json` | Contraste medido de cada campo de formulario |
| `reports/capturas/<pantalla>-<ancho>-<tema>.png` | Captura de página completa de cada combinación |
| `reports/lighthouse/*.html`, `resumen.json`, `resumen.md` | Reporte de Lighthouse por pantalla y perfil, y resumen |

En CI (job `ui-accesibilidad`) se generan solos y quedan en el artefacto `ui-accesibilidad-evidence`. Resultados y análisis: [docs/ACCESIBILIDAD_RESPONSIVE.md](../docs/ACCESIBILIDAD_RESPONSIVE.md).

## Para agregar una pantalla

Sumar su ruta a `PANTALLAS` en los tres archivos de `tests/` y en `lighthouse.mjs`, y su rol a `ROL_POR_PAGINA` en `servidor-mock.mjs` (`null` si es pública). Si muestra datos, agregar su respuesta simulada en `respuestaApi`.
