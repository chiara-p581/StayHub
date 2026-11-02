// Lighthouse (categoría Accesibilidad) sobre las pantallas principales, en móvil (360 px) y escritorio
// (1280 px). Criterio de la cátedra: puntaje >= 90. Sale con código 1 si alguna pantalla no llega.
//
// Usa el servidor de pruebas (servidor-mock.mjs) y el Chromium que instala Playwright, así que no
// necesita Docker ni Chrome del sistema.
import { launch } from "chrome-launcher";
import lighthouse from "lighthouse";
import { chromium } from "@playwright/test";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { iniciarServidor } from "./servidor-mock.mjs";

const UMBRAL = Number(process.env.LH_UMBRAL || 90);
const PUERTO = Number(process.env.LH_PORT || 4174); // tema claro; el oscuro usa el puerto siguiente
const TEMAS = ["claro", "oscuro"];
const puertoDe = (tema) => PUERTO + (tema === "oscuro" ? 1 : 0);
const aca = path.dirname(fileURLToPath(import.meta.url));
const SALIDA = path.join(aca, "reports", "lighthouse");
mkdirSync(SALIDA, { recursive: true });

const PANTALLAS = [
  "index.html", "login.html", "register.html", "recover-password.html", "reset-password.html#token=token-de-prueba",
  "hotel-details.html?id=1", "cart.html", "checkout.html", "reservation-details.html?id=501", "settings.html",
  "about-us.html", "help-faq.html",
  "admin-dashboard.html", "inventory.html", "hotel-management.html", "channels-sync.html",
  "payments.html", "alerts.html", "staff-portal.html"
];

const PERFILES = {
  movil: { formFactor: "mobile", screenEmulation: { mobile: true, width: 360, height: 740, deviceScaleFactor: 2, disabled: false } },
  escritorio: { formFactor: "desktop", screenEmulation: { mobile: false, width: 1280, height: 800, deviceScaleFactor: 1, disabled: false } }
};

// Cada ejecución de Lighthouse tarda varios segundos aunque la página sea local. Lighthouse no admite
// ejecuciones simultáneas dentro de un mismo proceso de Node, así que las 38 mediciones (19 pantallas
// x 2 perfiles) se reparten entre procesos hijos, cada uno con su propia instancia de Chrome.
const TRABAJADORES = Number(process.env.LH_WORKERS || 4);
const TODAS_LAS_TAREAS = TEMAS.flatMap((tema) => PANTALLAS.flatMap((pantalla) => Object.keys(PERFILES).map((perfil) => ({ pantalla, perfil, tema }))));

async function medir(chrome, { pantalla, perfil, tema }) {
  const id = pantalla.replace(/\.html.*$/, "");
  const emulacion = PERFILES[perfil];
  const resultado = await lighthouse(`http://127.0.0.1:${puertoDe(tema)}/${pantalla}`, {
    port: chrome.port, output: "html", logLevel: "error", onlyCategories: ["accessibility"]
  }, {
    extends: "lighthouse:default",
    settings: {
      onlyCategories: ["accessibility"], throttlingMethod: "provided", disableStorageReset: false,
      // Servidor local sin latencia: no hace falta esperar tanto a que la página "se calme"
      pauseAfterFcpMs: 250, pauseAfterLoadMs: 250, networkQuietThresholdMs: 250, cpuQuietThresholdMs: 250,
      maxWaitForFcp: 15_000, maxWaitForLoad: 20_000,
      formFactor: emulacion.formFactor, screenEmulation: emulacion.screenEmulation
    }
  });
  const lhr = resultado.lhr;
  const puntaje = Math.round((lhr.categories.accessibility.score ?? 0) * 100);
  const fallas = Object.values(lhr.audits).filter((a) => a.scoreDisplayMode === "binary" && a.score === 0).map((a) => a.id);
  writeFileSync(path.join(SALIDA, `${id}-${perfil}-${tema}.html`), resultado.report);
  return { pantalla, perfil, tema, puntaje, fallas };
}

// Modo hijo: mide la porción "i/N" de las tareas y deja el resultado parcial en disco
if (process.env.LH_SHARD) {
  const [indice, total] = process.env.LH_SHARD.split("/").map(Number);
  const mias = TODAS_LAS_TAREAS.filter((_, n) => n % total === indice);
  const chrome = await launch({
    chromePath: chromium.executablePath(),
    // Movimiento reducido: igual que en las pruebas de Playwright, evita medir elementos a mitad de animación
    chromeFlags: ["--headless=new", "--no-sandbox", "--disable-gpu", "--force-prefers-reduced-motion"]
  });
  const salida = [];
  try {
    for (const tarea of mias) salida.push(await medir(chrome, tarea));
  } finally {
    await chrome.kill();
  }
  writeFileSync(path.join(SALIDA, `parcial-${indice}.json`), JSON.stringify(salida));
  process.exit(0);
}

// Modo principal: levanta el servidor, lanza los hijos y junta sus resultados
const servidores = await Promise.all(TEMAS.map((tema) => iniciarServidor(puertoDe(tema), { tema })));
const resultados = new Map(); // "pantalla|perfil" -> { puntaje, fallas }
try {
  await Promise.all(Array.from({ length: TRABAJADORES }, (_, n) => new Promise((resolver, rechazar) => {
    const hijo = spawn(process.execPath, [fileURLToPath(import.meta.url)], {
      env: { ...process.env, LH_SHARD: `${n}/${TRABAJADORES}`, LH_PORT: String(PUERTO) }, stdio: ["ignore", "inherit", "inherit"]
    });
    hijo.on("exit", (codigo) => (codigo === 0 ? resolver() : rechazar(new Error(`El trabajador ${n} terminó con código ${codigo}`))));
  })));
  for (let n = 0; n < TRABAJADORES; n++) {
    for (const r of JSON.parse(readFileSync(path.join(SALIDA, `parcial-${n}.json`), "utf8"))) {
      resultados.set(`${r.pantalla}|${r.perfil}|${r.tema}`, { puntaje: r.puntaje, fallas: r.fallas });
    }
  }
} finally {
  servidores.forEach((srv) => srv.close());
}

const COLUMNAS = TEMAS.flatMap((tema) => Object.keys(PERFILES).map((perfil) => ({ tema, perfil, clave: `${perfil}|${tema}` })));
const filas = PANTALLAS.map((pantalla) => {
  const fila = { pantalla: pantalla.replace(/\.html.*$/, "") };
  for (const c of COLUMNAS) fila[c.clave] = resultados.get(`${pantalla}|${c.clave}`);
  const fallas = [...new Set(COLUMNAS.flatMap((c) => fila[c.clave].fallas))];
  console.log(`${fila.pantalla.padEnd(22)} ` + COLUMNAS.map((c) => `${c.perfil.slice(0, 3)}/${c.tema.slice(0, 3)} ${String(fila[c.clave].puntaje).padStart(3)}`).join("  ") + (fallas.length ? `  fallas: ${fallas.join(", ")}` : ""));
  return fila;
});

const todos = filas.flatMap((f) => COLUMNAS.map((c) => f[c.clave].puntaje));
const minimo = Math.min(...todos);
const promedio = Math.round(todos.reduce((a, b) => a + b, 0) / todos.length);

writeFileSync(path.join(SALIDA, "resumen.json"), JSON.stringify({
  fecha: new Date().toISOString(), umbral: UMBRAL, minimo, promedio, mediciones: todos.length, pantallas: filas
}, null, 2));
const md = [
  "### Lighthouse: accesibilidad por pantalla",
  `Umbral: **${UMBRAL}**. Mínimo obtenido: **${minimo}**. Promedio: **${promedio}**. Mediciones: ${todos.length}.`,
  "",
  "| Pantalla | Móvil 360 px, claro | Escritorio 1280 px, claro | Móvil 360 px, oscuro | Escritorio 1280 px, oscuro |",
  "|---|---|---|---|---|",
  ...filas.map((f) => `| ${f.pantalla} | ${COLUMNAS.map((c) => f[c.clave].puntaje).join(" | ")} |`)
].join("\n");
writeFileSync(path.join(SALIDA, "resumen.md"), md + "\n");
console.log(`\nMínimo ${minimo}, promedio ${promedio}, umbral ${UMBRAL}`);

const bajo = filas.filter((f) => COLUMNAS.some((c) => f[c.clave].puntaje < UMBRAL));
if (bajo.length) {
  console.error(`\nPantallas por debajo de ${UMBRAL}: ${bajo.map((f) => f.pantalla).join(", ")}`);
  process.exit(1);
}
