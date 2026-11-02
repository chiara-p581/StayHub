// Eje A (Criterios de Testing, sección 4): responsive verificado a 360, 768 y 1280 px y
// accesibilidad sin violaciones críticas (WCAG 2.2 AA). Cada pantalla se abre en los tres anchos.
const { test, expect } = require("@playwright/test");
const AxeBuilder = require("@axe-core/playwright").default;
const fs = require("node:fs");
const path = require("node:path");

const VIEWPORTS = [
  { nombre: "360", width: 360, height: 740 },
  { nombre: "768", width: 768, height: 1024 },
  { nombre: "1280", width: 1280, height: 800 }
];

// Pantallas principales. La ruta lleva los parámetros que necesita cada una para mostrar datos.
const PANTALLAS = [
  "index.html", "login.html", "register.html", "recover-password.html", "reset-password.html#token=token-de-prueba",
  "hotel-details.html?id=1", "cart.html", "checkout.html", "reservation-details.html?id=501", "settings.html",
  "about-us.html", "help-faq.html",
  "admin-dashboard.html", "inventory.html", "hotel-management.html", "channels-sync.html",
  "payments.html", "alerts.html", "staff-portal.html"
];

const TEMAS = ["claro", "oscuro"];
const ETIQUETAS_WCAG = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];
const CARPETA = path.resolve(__dirname, "../reports");
fs.mkdirSync(path.join(CARPETA, "axe"), { recursive: true });
fs.mkdirSync(path.join(CARPETA, "capturas"), { recursive: true });

for (const pantalla of PANTALLAS) {
  const id = pantalla.replace(/\.html.*$/, "");
  for (const tema of TEMAS) for (const vp of VIEWPORTS) {
    test(`${id} a ${vp.nombre} px, tema ${tema}: sin desbordes y sin violaciones de accesibilidad graves`, async ({ browser }) => {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        reducedMotion: "reduce",
        // En 360 px se simula un teléfono para que valgan los medios táctiles (hover: none)
        hasTouch: vp.width <= 768,
        isMobile: vp.width <= 768
      });
      if (tema === "oscuro") await context.addInitScript(() => localStorage.setItem("stayhub_theme", "dark"));
      const page = await context.newPage();
      const erroresDeConsola = [];
      page.on("pageerror", (e) => erroresDeConsola.push(e.message));

      await page.goto(pantalla, { waitUntil: "networkidle" });
      await page.evaluate(() => document.fonts.ready);
      // Garantía de que se mide el tema pedido (un tema que no se aplica haría pasar la prueba sin probar nada)
      expect(await page.evaluate(() => document.documentElement.dataset.theme), "el tema pedido no quedó aplicado")
        .toBe(tema === "oscuro" ? "dark" : "light");

      // 1) Sin desplazamiento horizontal: nada se sale del ancho de la pantalla
      const medidas = await page.evaluate(() => ({
        ancho: document.documentElement.clientWidth,
        contenido: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)
      }));
      expect(medidas.contenido, `desborde horizontal: el contenido mide ${medidas.contenido}px y la pantalla ${medidas.ancho}px`)
        .toBeLessThanOrEqual(medidas.ancho + 1);

      // 2) El viewport debe permitir zoom (WCAG 1.4.4) y no bloquear la escala
      const metaViewport = await page.locator('meta[name="viewport"]').getAttribute("content");
      expect(metaViewport, "falta la etiqueta meta viewport").toBeTruthy();
      expect(metaViewport).not.toMatch(/user-scalable\s*=\s*(no|0)|maximum-scale\s*=\s*1(\.0)?\b/);

      // 3) axe-core con las reglas WCAG 2.0, 2.1 y 2.2 de nivel A y AA
      const resultado = await new AxeBuilder({ page }).withTags(ETIQUETAS_WCAG).analyze();
      const porImpacto = (nivel) => resultado.violations.filter((v) => v.impact === nivel);
      const resumen = (lista) => lista.map((v) => `${v.id} (${v.nodes.length} nodos): ${v.help}`).join("\n");

      fs.writeFileSync(path.join(CARPETA, "axe", `${id}-${vp.nombre}-${tema}.json`), JSON.stringify({
        pantalla, ancho: vp.width, tema, url: page.url(), fecha: new Date().toISOString(),
        etiquetas: ETIQUETAS_WCAG, reglasEvaluadas: resultado.passes.length + resultado.violations.length,
        violaciones: resultado.violations.map((v) => ({
          id: v.id, impacto: v.impact, descripcion: v.help, ayuda: v.helpUrl, etiquetas: v.tags,
          nodos: v.nodes.map((n) => ({ destino: n.target, resumen: n.failureSummary }))
        })),
        incompletas: resultado.incomplete.map((v) => v.id)
      }, null, 2));

      await page.screenshot({ path: path.join(CARPETA, "capturas", `${id}-${vp.nombre}-${tema}.png`), fullPage: true });
      await context.close();

      // Criterio de la cátedra: 0 violaciones críticas. Se exige también 0 graves (serious).
      expect(porImpacto("critical"), `Violaciones críticas:\n${resumen(porImpacto("critical"))}`).toHaveLength(0);
      expect(porImpacto("serious"), `Violaciones graves:\n${resumen(porImpacto("serious"))}`).toHaveLength(0);
      expect(erroresDeConsola, `Errores de JavaScript en la página:\n${erroresDeConsola.join("\n")}`).toHaveLength(0);
    });
  }
}
