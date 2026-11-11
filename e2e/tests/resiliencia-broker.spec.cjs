// KAN-91 — Recuperación ante caída del broker JMS (Criterios de Testing, sección 6).
//
// El broker de StayHub es el Artemis embebido en WildFly (standalone-full), por eso
// "apagar el broker" equivale a matar el contenedor `app` con SIGKILL: es una caída
// abrupta, sin apagado ordenado. La prueba verifica los tres criterios del ticket:
//   1. No se pierden mensajes persistentes que estaban en cola.
//   2. El sistema se recupera solo: al volver, consume la cola sin intervención.
//   3. La operación del usuario (pago → confirmación de reserva → evento JMS) no queda
//      a medias, y reintentarla con la misma Idempotency-Key no duplica nada.
//
// Requiere el entorno de docker compose levantado y STAYHUB_DOCKER_VERIFY=true.
const { test, expect } = require("@playwright/test");
const { execFile, execFileSync } = require("node:child_process");
const { promisify } = require("node:util");
const crypto = require("node:crypto");
const path = require("node:path");

const execFileAsync = promisify(execFile);
const repo = path.resolve(__dirname, "../..");
const origin = process.env.STAYHUB_ORIGIN || new URL(process.env.STAYHUB_BASE_URL || "https://localhost:8443/StayHub").origin;
const dockerVerification = process.env.STAYHUB_DOCKER_VERIFY === "true";
const adminEmail = process.env.STAYHUB_ADMIN_EMAIL || "admin@stayhub.local";
const adminPassword = process.env.STAYHUB_ADMIN_PASSWORD || "StayHubDemo2026!";
const adminMfaSecret = process.env.STAYHUB_ADMIN_MFA_SECRET || "";

const MENSAJES_EN_COLA = 5;   // sincronizaciones encoladas antes de la caída
const PAGOS_EN_VUELO = 4;     // pagos que se disparan justo antes de matar el broker
const CANAL = "EXPEDIA";      // canal exclusivo de esta prueba para contar sin ruido en el mock
const RUTA_MOCK = "/expedia/disponibilidad";
const MDB = "/deployment=StayHub.war/subsystem=ejb3/message-driven-bean=ConsumidorSincronizacion";
const COLA = "CanalesExternosSincronizaciones";

function isoDate(days) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function docker(...args) {
  return execFileSync("docker", ["compose", ...args], { cwd: repo, encoding: "utf8", maxBuffer: 32 * 1024 * 1024 });
}

function dbScalar(sql) {
  return docker("exec", "-T", "postgres", "psql", "-U", "stayhub", "-d", "stayhub", "-tA", "-c", sql).trim();
}

function cli(command) {
  return docker("exec", "-T", "app", "/opt/jboss/wildfly/bin/jboss-cli.sh", "--connect", `--command=${command}`);
}

/** Cantidad de mensajes en la cola según Artemis, o null si la ruta de gestión no está disponible. */
function mensajesEnCola() {
  const rutas = [
    `/deployment=StayHub.war/subsystem=messaging-activemq/server=default/jms-queue=${COLA}`,
    `/subsystem=messaging-activemq/server=default/jms-queue=${COLA}`,
    `/subsystem=messaging-activemq/server=default/runtime-queue=jms.queue.${COLA}`
  ];
  for (const ruta of rutas) {
    try {
      const salida = cli(`${ruta}:read-attribute(name=message-count)`);
      const valor = salida.match(/"result" => (\d+)L?/);
      if (salida.includes("success") && valor) return Number(valor[1]);
    } catch {
      // se prueba la ruta siguiente
    }
  }
  return null;
}

function sincronizacionesRecibidasPorMock() {
  return docker("logs", "--no-color", "ota-mock")
    .split("\n")
    .filter(linea => linea.includes("[OTA] Sincronización recibida") && linea.includes(RUTA_MOCK)).length;
}

async function csrf(request) {
  const response = await request.get("api/seguridad/csrf");
  expect(response.ok()).toBeTruthy();
  return (await response.json()).token;
}

async function mutate(request, method, url, data, extraHeaders = {}) {
  const token = await csrf(request);
  return request.fetch(url, {
    method,
    data,
    headers: { Origin: origin, "X-CSRF-Token": token, "Content-Type": "application/json", ...extraHeaders }
  });
}

/** Login con segundo factor: el administrador demo tiene MFA (TOTP) obligatorio. */
async function login(request, email, password) {
  const response = await mutate(request, "POST", "api/usuarios/login", { email, password });
  const raw = await response.text();
  expect(response.status(), raw).toBe(200);
  const user = JSON.parse(raw);
  if (user.mfaRequerido) {
    expect(user.mfaConfigurado, "El administrador demo debe tener MFA activado").toBe(true);
    expect(adminMfaSecret, "Falta STAYHUB_ADMIN_MFA_SECRET").not.toBe("");
    const verificacion = await mutate(request, "POST", "api/usuarios/mfa/verificar-login", { codigo: totp(adminMfaSecret) });
    expect(verificacion.status(), await verificacion.text()).toBe(200);
  }
}

function totp(secret) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let buffer = 0, bits = 0;
  const bytes = [];
  for (const char of secret.toUpperCase()) {
    const value = alphabet.indexOf(char);
    if (value < 0) throw new Error("Invalid Base32 MFA secret");
    buffer = (buffer << 5) | value;
    bits += 5;
    if (bits >= 8) bytes.push((buffer >> (bits -= 8)) & 0xff);
  }
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30)));
  const hash = crypto.createHmac("sha1", Buffer.from(bytes)).update(counter).digest();
  const offset = hash[hash.length - 1] & 15;
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, "0");
}

/** Busca rangos de dos noches con cupo STANDARD, sin pisar los que usan otras pruebas (días 10 a 45). */
async function rangosConCupo(request, hotelId, cantidad) {
  const rangos = [];
  for (let dias = 60; dias <= 170 && rangos.length < cantidad; dias += 3) {
    const checkIn = isoDate(dias), checkOut = isoDate(dias + 2);
    const r = await request.get(`api/inventario-tarifas/disponibilidad?hotelId=${hotelId}&desde=${checkIn}&hasta=${checkOut}`);
    expect(r.ok(), await r.text()).toBeTruthy();
    const standard = (await r.json()).find(item => item.tipoHabitacion === "STANDARD");
    if (standard?.unidadesDisponibles > 0) rangos.push({ checkIn, checkOut });
  }
  expect(rangos.length, "No hay suficientes rangos STANDARD con cupo").toBe(cantidad);
  return rangos;
}

async function esperarApp(request) {
  await expect.poll(async () => {
    try {
      return (await request.get("api/hoteles", { timeout: 5_000 })).status();
    } catch {
      return 0;
    }
  }, { timeout: 240_000, intervals: [2_000] }).toBe(200);
}

function invariantesDeConsistencia(reservaIds) {
  const ids = reservaIds.map(Number).join(",");
  return {
    // Una reserva está CONFIRMADA si y sólo si tiene un pago APROBADO.
    reservasIncoherentes: Number(dbScalar(`SELECT count(*) FROM reserva r WHERE r.id IN (${ids})
      AND ((r.estado = 'CONFIRMADA') <> EXISTS (SELECT 1 FROM pago p WHERE p.reserva_id = r.id AND p.estado = 'APROBADO'))`)),
    // Ningún pago quedó a mitad de camino.
    pagosPendientes: Number(dbScalar(`SELECT count(*) FROM pago WHERE reserva_id IN (${ids}) AND estado = 'PENDIENTE'`)),
    // Ninguna reserva cobrada dos veces.
    reservasConPagoDuplicado: Number(dbScalar(`SELECT count(*) FROM (SELECT reserva_id FROM pago
      WHERE reserva_id IN (${ids}) AND estado = 'APROBADO' GROUP BY reserva_id HAVING count(*) > 1) d`)),
    // Ninguna reserva en un estado inesperado.
    reservasEnOtroEstado: Number(dbScalar(`SELECT count(*) FROM reserva WHERE id IN (${ids})
      AND estado NOT IN ('PENDIENTE', 'CONFIRMADA')`))
  };
}

test.describe("KAN-91 — recuperación ante caída del broker JMS", () => {
  test.skip(!dockerVerification, "Requiere el entorno docker compose y STAYHUB_DOCKER_VERIFY=true");

  // Pase lo que pase, el broker queda levantado para la prueba siguiente (o el reintento).
  test.afterEach(async ({ request }) => {
    test.setTimeout(300_000);
    docker("start", "app");
    await esperarApp(request);
  });

  test("caída abrupta del broker: sin pérdida de mensajes, recuperación automática y operación consistente",
    async ({ request }, testInfo) => {
      test.setTimeout(420_000);
      const hotel = await test.step("preparar datos", async () => {
        await login(request, adminEmail, adminPassword);
        const response = await request.get("api/hoteles");
        expect(response.ok()).toBeTruthy();
        return (await response.json())[0];
      });
      const desde = isoDate(10), hasta = isoDate(12);

      // ---- Criterio 1: mensajes persistentes en cola al momento de la caída ----
      const recibidasAntes = sincronizacionesRecibidasPorMock();
      await test.step("detener el consumo y encolar mensajes persistentes", async () => {
        expect(cli(`${MDB}:stop-delivery`)).toContain("success");
        for (let i = 0; i < MENSAJES_EN_COLA; i++) {
          const r = await mutate(request, "POST",
            `api/canales-externos/otas/${CANAL}/sincronizaciones?hotelId=${hotel.id}&desde=${desde}&hasta=${hasta}`, {});
          expect(r.status(), await r.text()).toBe(202);
        }
        // Con el consumo detenido, nada llega al mock: los mensajes están sólo en el broker.
        await new Promise(r => setTimeout(r, 3_000));
        expect(sincronizacionesRecibidasPorMock()).toBe(recibidasAntes);
        const enCola = mensajesEnCola();
        testInfo.annotations.push({ type: "mensajes en cola antes de la caída", description: String(enCola ?? "no disponible") });
        if (enCola !== null) expect(enCola).toBeGreaterThanOrEqual(MENSAJES_EN_COLA);
      });

      // ---- Criterio 3 (parte 1): operación del usuario en vuelo durante la caída ----
      const guest = { email: `kan91.${Date.now()}.${crypto.randomInt(1000, 9999)}@stayhub.local`, password: "StayHubE2E.2026!" };
      const reservas = await test.step("crear reservas pendientes de pago", async () => {
        const alta = await mutate(request, "POST", "api/usuarios", {
          email: guest.email, password: guest.password, nombre: "KAN91", apellido: "Broker"
        });
        expect(alta.status(), await alta.text()).toBe(201);
        await login(request, guest.email, guest.password);
        const creadas = [];
        const rangos = await rangosConCupo(request, hotel.id, PAGOS_EN_VUELO);
        for (let i = 0; i < PAGOS_EN_VUELO; i++) {
          const claveReserva = `kan91-reserva-${i}-${Date.now()}`;
          const r = await mutate(request, "POST", "api/reservas", {
            hotelId: hotel.id, tipoHabitacion: "STANDARD", cantidadHabitaciones: 1,
            checkIn: rangos[i].checkIn, checkOut: rangos[i].checkOut,
            huespedNombre: "KAN91", huespedApellido: "Broker", huespedEmail: guest.email,
            huespedTelefono: "1155550091", precioTotal: 1, moneda: "USD"
          }, { "Idempotency-Key": claveReserva });
          expect(r.status(), await r.text()).toBe(201);
          const reserva = await r.json();
          creadas.push({ ...reserva, clave: `kan91-${reserva.id}-${Date.now()}` });
        }
        return creadas;
      });

      const csrfPago = await csrf(request);
      const resultadosEnVuelo = await test.step("disparar pagos y matar el broker durante el procesamiento", async () => {
        const pagos = reservas.map(r => request.fetch("api/pagos", {
          method: "POST",
          data: { reservaId: r.id, monto: r.precioTotal, moneda: r.moneda, claveIdempotencia: r.clave },
          headers: { Origin: origin, "X-CSRF-Token": csrfPago, "Content-Type": "application/json", "Idempotency-Key": r.clave },
          timeout: 30_000
        })
          // El corte de conexión es el efecto esperado de la caída: se registra en el
          // momento, para que no quede como rechazo sin manejar mientras se mata el broker.
          .then(respuesta => respuesta.status(), () => "sin respuesta"));
        await new Promise(r => setTimeout(r, 50)); // deja salir las solicitudes
        await execFileAsync("docker", ["compose", "kill", "app"], { cwd: repo }); // SIGKILL: caída abrupta
        return Promise.all(pagos);
      });
      testInfo.annotations.push({ type: "pagos al momento de la caída", description: JSON.stringify(resultadosEnVuelo) });

      // ---- Criterio 2: el sistema vuelve solo ----
      await test.step("volver a levantar el broker", async () => {
        docker("start", "app");
        await esperarApp(request);
      });

      await test.step("inmediatamente después de la caída, ninguna operación quedó a medias", async () => {
        expect(invariantesDeConsistencia(reservas.map(r => r.id))).toEqual({
          reservasIncoherentes: 0, pagosPendientes: 0, reservasConPagoDuplicado: 0, reservasEnOtroEstado: 0
        });
      });

      await test.step("los mensajes encolados antes de la caída se procesan sin intervención", async () => {
        await expect.poll(() => sincronizacionesRecibidasPorMock() - recibidasAntes, { timeout: 120_000, intervals: [3_000] })
          .toBeGreaterThanOrEqual(MENSAJES_EN_COLA);
        await expect.poll(() => mensajesEnCola() ?? 0, { timeout: 60_000, intervals: [3_000] }).toBe(0);
      });

      // ---- Criterio 3 (parte 2): el usuario reintenta y todo converge ----
      await test.step("el usuario reintenta con la misma clave de idempotencia", async () => {
        await login(request, guest.email, guest.password);
        for (const [i, r] of reservas.entries()) {
          const pago = await mutate(request, "POST", "api/pagos", {
            reservaId: r.id, monto: r.precioTotal, moneda: r.moneda, claveIdempotencia: r.clave
          }, { "Idempotency-Key": r.clave });
          expect(pago.status(), `reserva ${r.id} (estado en la caída: ${resultadosEnVuelo[i]}): ${await pago.text()}`).toBe(201);
          expect((await pago.json()).estado).toBe("APROBADO");
        }
        const ids = reservas.map(r => Number(r.id)).join(",");
        expect(Number(dbScalar(`SELECT count(*) FROM reserva WHERE id IN (${ids}) AND estado = 'CONFIRMADA'`))).toBe(PAGOS_EN_VUELO);
        expect(Number(dbScalar(`SELECT count(*) FROM pago WHERE reserva_id IN (${ids})`))).toBe(PAGOS_EN_VUELO);
        expect(invariantesDeConsistencia(reservas.map(r => r.id))).toEqual({
          reservasIncoherentes: 0, pagosPendientes: 0, reservasConPagoDuplicado: 0, reservasEnOtroEstado: 0
        });
      });

      await test.step("la mensajería sigue operativa después de la recuperación", async () => {
        await login(request, adminEmail, adminPassword);
        const antes = sincronizacionesRecibidasPorMock();
        const r = await mutate(request, "POST",
          `api/canales-externos/otas/${CANAL}/sincronizaciones?hotelId=${hotel.id}&desde=${desde}&hasta=${hasta}`, {});
        expect(r.status(), await r.text()).toBe(202);
        await expect.poll(() => sincronizacionesRecibidasPorMock() - antes, { timeout: 60_000, intervals: [2_000] })
          .toBeGreaterThanOrEqual(1);
      });
    });
});
