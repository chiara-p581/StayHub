// Servidor de pruebas de UI: sirve las pantallas de StayHub (proyecto/src/main/webapp), simula la
// API REST y fija la sesión del usuario. No necesita Docker, base de datos ni broker, así que las
// mismas pantallas se pueden recorrer con Playwright (axe) y con Lighthouse de forma repetible.
//
// Es solo un arnés de pruebas: no forma parte del producto. La sesión se fija inyectando en el
// HTML un script que escribe en sessionStorage, igual que lo haría el login real.
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const aca = path.dirname(fileURLToPath(import.meta.url));
const RAIZ_WEB = path.resolve(aca, "../proyecto/src/main/webapp");

const TIPOS_MIME = {
  ".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8", ".json": "application/json; charset=utf-8",
  ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".svg": "image/svg+xml",
  ".woff": "font/woff", ".woff2": "font/woff2", ".ttf": "font/ttf", ".ico": "image/x-icon"
};

const ADMIN = { id: 1, email: "admin@stayhub.local", nombre: "Ana", apellido: "Admin", rol: "ADMIN" };
const HUESPED = { id: 2, email: "huesped@stayhub.local", nombre: "Hugo", apellido: "Huésped", rol: "HUESPED" };

// Rol con el que se abre cada pantalla (null = sin sesión)
export const ROL_POR_PAGINA = {
  "index.html": "HUESPED", "hotel-details.html": "HUESPED", "about-us.html": "HUESPED",
  "help-faq.html": "HUESPED", "cart.html": "HUESPED", "checkout.html": "HUESPED",
  "reservation-details.html": "HUESPED", "settings.html": "HUESPED",
  "admin-dashboard.html": "ADMIN", "inventory.html": "ADMIN", "hotel-management.html": "ADMIN",
  "channels-sync.html": "ADMIN", "payments.html": "ADMIN", "alerts.html": "ADMIN",
  "staff-portal.html": "ADMIN",
  "login.html": null, "register.html": null, "recover-password.html": null, "reset-password.html": null
};

const dia = (n) => new Date(Date.now() + n * 86_400_000).toISOString().slice(0, 10);

const TIPOS = [
  { id: 11, codigo: "DOBLE", nombre: "Doble estándar", descripcion: "Cama doble, baño privado", capacidadMaxima: 2, activo: true, caracteristicas: ["WiFi", "Aire acondicionado"] },
  { id: 12, codigo: "SUITE", nombre: "Suite con vista", descripcion: "Sala de estar y balcón", capacidadMaxima: 4, activo: true, caracteristicas: ["WiFi", "Balcón"] }
];
const HOTELES = [
  { id: 1, nombre: "StayHub Resort Patagonia", direccion: "Av. del Lago 123", ciudad: "Bariloche", pais: "Argentina", descripcion: "Resort frente al lago.", activo: true, servicios: ["WiFi", "Spa", "Desayuno"], tiposHabitacion: TIPOS, habitaciones: [] },
  { id: 2, nombre: "StayHub Costa Atlántica", direccion: "Rambla 456", ciudad: "Mar del Plata", pais: "Argentina", descripcion: "A metros de la playa.", activo: true, servicios: ["WiFi", "Pileta"], tiposHabitacion: TIPOS, habitaciones: [] },
  { id: 3, nombre: "StayHub Centro Histórico", direccion: "Calle Florida 789", ciudad: "Buenos Aires", pais: "Argentina", descripcion: "En el microcentro.", activo: true, servicios: ["WiFi"], tiposHabitacion: TIPOS, habitaciones: [] }
];
const RESERVA = () => ({
  id: 501, canal: "STAYHUB", referenciaExterna: null, hotelId: 1, tipoHabitacion: "DOBLE", cantidadHabitaciones: 1,
  checkIn: dia(30), checkOut: dia(33), huespedNombre: "Hugo", huespedApellido: "Huésped",
  huespedEmail: "huesped@stayhub.local", precioTotal: 240000, moneda: "ARS", estado: "CONFIRMADA",
  fechaCreacion: new Date().toISOString().slice(0, 19)
});
const PAGO = () => ({
  id: 901, reservaId: 501, reservaIds: [501], monto: 240000, moneda: "ARS", estado: "APROBADO",
  referenciaPasarela: "PAS-0001", fechaPago: new Date().toISOString().slice(0, 19)
});

const CARRITO = () => [{
  itemId: "1-a1b2", hotelId: 1, hotelNombre: "StayHub Resort Patagonia", canal: "STAYHUB",
  checkIn: dia(30), checkOut: dia(33), tipoHabitacion: "DOBLE", cantidadHabitaciones: 1,
  precioTotal: 240000, moneda: "ARS", reservaId: 501
}];

// Respuestas GET de la API simulada, por ruta (sin el prefijo /api/)
function respuestaApi(ruta, metodo) {
  if (metodo !== "GET") return { estado: 200, cuerpo: {} };
  if (ruta === "seguridad/csrf") return { estado: 200, cuerpo: { token: "csrf-de-prueba" } };
  if (ruta === "hoteles") return { estado: 200, cuerpo: HOTELES };
  let m = ruta.match(/^hoteles\/(\d+)$/);
  if (m) return { estado: 200, cuerpo: HOTELES.find((h) => h.id === Number(m[1])) ?? HOTELES[0] };
  if (/^hoteles\/\d+\/tipos-habitacion$/.test(ruta)) return { estado: 200, cuerpo: TIPOS };
  if (/^hoteles\/\d+\/habitaciones$/.test(ruta)) return { estado: 200, cuerpo: [
    { id: 101, numero: "101", tipoHabitacionId: 11, activa: true }, { id: 102, numero: "102", tipoHabitacionId: 11, activa: true }] };
  if (ruta === "catalogo-canales/ofertas") return { estado: 200, cuerpo: HOTELES.flatMap((h, i) => [
    { hotelId: h.id, canal: "BOOKING", precioDesde: 80000 + i * 5000, moneda: "ARS", puntuacion: 8.7, opiniones: 120 + i },
    { hotelId: h.id, canal: "EXPEDIA", precioDesde: 82000 + i * 5000, moneda: "ARS", puntuacion: 8.4, opiniones: 90 + i }]) };
  if (ruta === "inventario-tarifas/disponibilidad") return { estado: 200, cuerpo: TIPOS.map((t) => (
    { hotelId: 1, tipoHabitacion: t.codigo, desde: dia(30), hasta: dia(33), unidadesDisponibles: 5 })) };
  if (ruta === "inventario-tarifas/tarifas") return { estado: 200, cuerpo: TIPOS.map((t, i) => (
    { hotelId: 1, tipoHabitacion: t.codigo, desde: dia(30), hasta: dia(33), importe: 80000 + i * 40000, moneda: "ARS" })) };
  if (ruta === "reservas/mias" || ruta === "reservas") return { estado: 200, cuerpo: [RESERVA()] };
  if (/^reservas\/\d+$/.test(ruta)) return { estado: 200, cuerpo: RESERVA() };
  if (ruta === "pagos/mios" || ruta === "pagos") return { estado: 200, cuerpo: [PAGO()] };
  if (/^pagos\/\d+$/.test(ruta)) return { estado: 200, cuerpo: PAGO() };
  m = ruta.match(/^usuarios\/(\d+)$/);
  if (m) return { estado: 200, cuerpo: Number(m[1]) === 1 ? ADMIN : HUESPED };
  if (ruta === "carrito") return { estado: 200, cuerpo: {} };
  return { estado: 200, cuerpo: [] };
}

function scriptDeSesion(pagina, tema) {
  const rol = ROL_POR_PAGINA[pagina] ?? null;
  const partes = [];
  // Una sola vez por pestaña: así lo que haga la pantalla (cerrar sesión, vaciar el carrito) se respeta
  // Sin tema indicado no se toca el localStorage: así Playwright puede elegirlo con addInitScript
  if (tema === "oscuro") partes.push(`localStorage.setItem("stayhub_theme", "dark");`);
  else if (tema === "claro") partes.push(`localStorage.setItem("stayhub_theme", "light");`);
  if (rol) partes.push(`sessionStorage.setItem("stayhub_user", ${JSON.stringify(JSON.stringify(rol === "ADMIN" ? ADMIN : HUESPED))});`);
  else partes.push(`sessionStorage.removeItem("stayhub_user");`);
  if (pagina === "cart.html" || pagina === "checkout.html") {
    partes.push(`localStorage.setItem("stayhub_cart_v2", ${JSON.stringify(JSON.stringify(CARRITO()))});`);
  }
  return `<script>try{${partes.join("")}}catch(e){}</script>`;
}

async function servirArchivo(res, ruta, tema) {
  let archivo = path.join(RAIZ_WEB, ruta === "/" ? "index.html" : decodeURIComponent(ruta));
  if (!archivo.startsWith(RAIZ_WEB)) { res.writeHead(403).end(); return; }
  try {
    if ((await stat(archivo)).isDirectory()) archivo = path.join(archivo, "index.html");
    let datos = await readFile(archivo);
    const ext = path.extname(archivo).toLowerCase();
    if (ext === ".html") {
      const html = datos.toString("utf8");
      const inyeccion = scriptDeSesion(path.basename(archivo), tema);
      datos = Buffer.from(/<head[^>]*>/i.test(html) ? html.replace(/<head[^>]*>/i, (h) => h + inyeccion) : inyeccion + html);
    }
    res.writeHead(200, { "Content-Type": TIPOS_MIME[ext] ?? "application/octet-stream", "Cache-Control": "no-store" });
    res.end(datos);
  } catch {
    res.writeHead(404, { "Content-Type": "text/plain" }).end("No encontrado");
  }
}

export function iniciarServidor(puerto = 4173, { tema = null } = {}) {
  const servidor = createServer(async (req, res) => {
    const url = new URL(req.url, "http://localhost");
    if (url.pathname.startsWith("/api/")) {
      const { estado, cuerpo } = respuestaApi(url.pathname.slice(5), req.method);
      res.writeHead(estado, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
      res.end(JSON.stringify(cuerpo));
      return;
    }
    await servirArchivo(res, url.pathname, tema);
  });
  return new Promise((resolver) => servidor.listen(puerto, "127.0.0.1", () => resolver(servidor)));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const puerto = Number(process.env.PORT || 4173);
  const tema = process.env.TEMA === "oscuro" ? "oscuro" : process.env.TEMA === "claro" ? "claro" : null;
  await iniciarServidor(puerto, { tema });
  console.log(`Servidor de UI de prueba (tema ${tema ?? "elegido por la prueba"}) en http://127.0.0.1:${puerto}`);
}
