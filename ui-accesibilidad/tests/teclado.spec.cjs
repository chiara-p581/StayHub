// Navegación por teclado (WCAG 2.1.1, 2.1.2, 2.4.7 y 2.4.11): lo que axe no puede comprobar por sí solo.
//   - Todo control que recibe el foco con Tab muestra un indicador visible (algún estilo cambia con el foco).
//   - El encabezado fijo no tapa el elemento enfocado (WCAG 2.2, 2.4.11 "foco no oculto").
//   - No hay trampas de teclado: el foco recorre la página y sale al final.
const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const PANTALLAS = [
  "index.html", "login.html", "register.html", "recover-password.html", "reset-password.html#token=token-de-prueba",
  "hotel-details.html?id=1", "cart.html", "checkout.html", "reservation-details.html?id=501", "settings.html",
  "about-us.html", "help-faq.html",
  "admin-dashboard.html", "inventory.html", "hotel-management.html", "channels-sync.html",
  "payments.html", "alerts.html", "staff-portal.html"
];
const CASOS = [
  { ancho: 1280, alto: 800, tema: "claro" },
  { ancho: 360, alto: 740, tema: "claro" },
  { ancho: 1280, alto: 800, tema: "oscuro" }
];
const MAX_TABS = 160;

const CARPETA = path.resolve(__dirname, "../reports/teclado");
fs.mkdirSync(CARPETA, { recursive: true });

// Se inyecta en la página: instantánea de los estilos que suelen señalar el foco
const INSTALAR_AYUDAS = () => {
  window.__foto = (e) => {
    const c = getComputedStyle(e);
    return ["outlineStyle", "outlineWidth", "outlineColor", "boxShadow", "backgroundColor", "borderTopColor",
      "borderBottomColor", "textDecorationLine", "color", "filter", "transform"].map((p) => c[p]).join("|");
  };
};

// Se ejecuta dentro de la página: describe el elemento enfocado y lo marca para medirlo después sin foco.
// No se hace blur()/focus() para no alterar el recorrido (un campo de fecha vuelve a su primer segmento).
function describirFoco(indice) {
  const e = document.activeElement;
  if (!e || e === document.body || e === document.documentElement) return null;
  e.setAttribute("data-kb-indice", String(indice));
  const rect = e.getBoundingClientRect();
  const cx = Math.min(Math.max(rect.left + rect.width / 2, 1), innerWidth - 1);
  const cy = Math.min(Math.max(rect.top + rect.height / 2, 1), innerHeight - 1);
  const encima = document.elementFromPoint(cx, cy);
  const tapado = !!encima && !(encima === e || e.contains(encima) || encima.contains(e));
  const visible = rect.width > 0 && rect.height > 0 && getComputedStyle(e).visibility !== "hidden";
  const nombre = e.tagName.toLowerCase() + (e.id ? "#" + e.id : "") + (e.getAttribute("aria-label") ? `[${e.getAttribute("aria-label")}]` : "")
    + (!e.id && !e.getAttribute("aria-label") ? "." + String(e.className).split(" ")[0].slice(0, 30) : "");
  return { nombre, visible, tapado, tapadoPor: tapado ? String(encima.className).split(" ")[0] : "", conFoco: window.__foto(e) };
}

// Mide el estilo de cada elemento marcado una vez que el foco salió de la página
function medirSinFoco() {
  if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
  const salida = {};
  document.querySelectorAll("[data-kb-indice]").forEach((e) => { salida[e.getAttribute("data-kb-indice")] = window.__foto(e); });
  return salida;
}

for (const pantalla of PANTALLAS) {
  const id = pantalla.replace(/\.html.*$/, "");
  for (const caso of CASOS) {
    test(`${id} a ${caso.ancho} px, tema ${caso.tema}: foco visible, no tapado y sin trampas`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: { width: caso.ancho, height: caso.alto }, reducedMotion: "reduce" });
      if (caso.tema === "oscuro") await context.addInitScript(() => localStorage.setItem("stayhub_theme", "dark"));
      const page = await context.newPage();
      await page.goto(pantalla, { waitUntil: "networkidle" });
      await page.evaluate(INSTALAR_AYUDAS);
      expect(await page.evaluate(() => document.documentElement.dataset.theme), "el tema pedido no quedó aplicado")
        .toBe(caso.tema === "oscuro" ? "dark" : "light");

      const visitados = [];
      let ultimos = [];
      let salioAlFinal = false;
      for (let i = 0; i < MAX_TABS; i++) {
        await page.keyboard.press("Tab");
        const foco = await page.evaluate(describirFoco, i);
        if (!foco) { salioAlFinal = visitados.length > 0; break; } // el foco volvió al documento: recorrió todo
        visitados.push(foco);
        ultimos = [...ultimos, foco.nombre].slice(-12);
        // Una trampa mantiene el foco en el mismo control (un campo de fecha usa hasta 3 paradas)
        if (ultimos.length === 12 && new Set(ultimos).size === 1) break;
      }
      const sinFoco = await page.evaluate(medirSinFoco);
      await context.close();

      // Un control con foco visible cambia algún estilo (contorno, sombra, fondo, subrayado...) respecto de sin foco
      visitados.forEach((f, n) => { f.tieneIndicador = sinFoco[n] !== f.conFoco; });
      const sinIndicador = visitados.filter((f) => f.visible && !f.tieneIndicador).map((f) => f.nombre);
      const tapados = visitados.filter((f) => f.visible && f.tapado).map((f) => `${f.nombre} (tapado por .${f.tapadoPor})`);
      const trampa = ultimos.length === 12 && new Set(ultimos).size === 1;

      fs.writeFileSync(path.join(CARPETA, `${id}-${caso.ancho}-${caso.tema}.json`), JSON.stringify({
        pantalla, ancho: caso.ancho, tema: caso.tema, controlesRecorridos: visitados.length, salioAlFinal,
        sinIndicador: [...new Set(sinIndicador)], tapados, trampa
      }, null, 2));

      expect(visitados.length, "el teclado no llegó a ningún control").toBeGreaterThan(2);
      expect(trampa, `trampa de teclado en ${ultimos[0]}`).toBe(false);
      expect([...new Set(sinIndicador)], "controles sin indicador de foco visible").toEqual([]);
      expect(tapados, "controles tapados por el encabezado fijo u otra capa al recibir el foco").toEqual([]);
    });
  }
}
