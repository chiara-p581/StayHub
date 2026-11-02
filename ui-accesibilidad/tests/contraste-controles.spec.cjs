// WCAG 1.4.11 (contraste de componentes de interfaz, nivel AA): el límite de un campo de formulario
// debe contrastar al menos 3:1 con lo que lo rodea. axe-core no mide este criterio.
// Se acepta que el campo se distinga por su borde o por el color de su fondo.
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
const UMBRAL = 3;
const CARPETA = path.resolve(__dirname, "../reports/contraste-controles");
fs.mkdirSync(CARPETA, { recursive: true });

// Se ejecuta dentro de la página
function medirControles(umbral) {
  const numeros = (s) => (s.match(/[\d.]+/g) || []).map(Number);
  const color = (c) => { const [r, g, b, a = 1] = numeros(c); return { r, g, b, a }; };
  const luminancia = ({ r, g, b }) => {
    const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };
  const mezclar = (frente, fondo) => ({
    r: frente.r * frente.a + fondo.r * (1 - frente.a),
    g: frente.g * frente.a + fondo.g * (1 - frente.a),
    b: frente.b * frente.a + fondo.b * (1 - frente.a)
  });
  const fondoDe = (el) => {
    const capas = [];
    for (let a = el; a; a = a.parentElement) {
      const c = color(getComputedStyle(a).backgroundColor);
      if (c.a > 0) capas.push(c);
      if (c.a >= 1) break;
    }
    let base = { r: 255, g: 255, b: 255 };
    for (let i = capas.length - 1; i >= 0; i--) base = mezclar(capas[i], base);
    return base;
  };
  const contraste = (a, b) => { const [x, y] = [luminancia(a), luminancia(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };

  const medidos = [];
  const incumplen = [];
  for (const e of document.querySelectorAll("input,select,textarea")) {
    if (["checkbox", "radio", "hidden", "submit", "button", "range", "file"].includes(e.type)) continue;
    const r = e.getBoundingClientRect();
    const cs = getComputedStyle(e);
    if (r.width < 4 || r.height < 4 || cs.visibility === "hidden") continue;
    // Campos sin borde ni fondo propio que forman parte de un conjunto con su propio contenedor (buscador de portada)
    if (/\bborder-none\b/.test(e.className) && /\bbg-transparent\b/.test(e.className)) continue;
    const entorno = fondoDe(e.parentElement || e);
    const fondo = color(cs.backgroundColor);
    const ancho = parseFloat(cs.borderTopWidth);
    const borde = color(cs.borderTopColor);
    const conBorde = ancho > 0 && cs.borderTopStyle !== "none" && borde.a > 0;
    const porBorde = conBorde ? contraste(mezclar(borde, entorno), entorno) : 0;
    const porFondo = fondo.a > 0 ? contraste(mezclar(fondo, entorno), entorno) : 0;
    const mejor = Math.max(porBorde, porFondo);
    const nombre = `${e.tagName.toLowerCase()}${e.id ? "#" + e.id : ""}[${e.type}]`;
    medidos.push({ nombre, contraste: Math.round(mejor * 100) / 100 });
    if (mejor < umbral) incumplen.push(`${nombre} (contraste ${mejor.toFixed(2)}:1)`);
  }
  return { medidos, incumplen };
}

for (const pantalla of PANTALLAS) {
  const id = pantalla.replace(/\.html.*$/, "");
  for (const tema of ["claro", "oscuro"]) {
    test(`${id}, tema ${tema}: los campos de formulario se distinguen con contraste >= ${UMBRAL}:1`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: { width: 1280, height: 800 }, reducedMotion: "reduce" });
      if (tema === "oscuro") await context.addInitScript(() => localStorage.setItem("stayhub_theme", "dark"));
      const page = await context.newPage();
      await page.goto(pantalla, { waitUntil: "networkidle" });
      expect(await page.evaluate(() => document.documentElement.dataset.theme)).toBe(tema === "oscuro" ? "dark" : "light");
      const { medidos, incumplen } = await page.evaluate(medirControles, UMBRAL);
      await context.close();
      fs.writeFileSync(path.join(CARPETA, `${id}-${tema}.json`), JSON.stringify({ pantalla, tema, umbral: UMBRAL, medidos, incumplen }, null, 2));
      expect(incumplen, "campos cuyo límite no contrasta lo suficiente").toEqual([]);
    });
  }
}
