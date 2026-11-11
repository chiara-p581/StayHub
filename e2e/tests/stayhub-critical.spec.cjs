const { test, expect } = require("@playwright/test");
const { execFileSync } = require("node:child_process");
const crypto = require("node:crypto");
const path = require("node:path");

const repo = path.resolve(__dirname, "../..");
const origin = process.env.STAYHUB_ORIGIN || new URL(process.env.STAYHUB_BASE_URL || "https://localhost:8443/StayHub").origin;
const webhookSecret = process.env.STAYHUB_WEBHOOK_SECRET || "demo-booking-webhook-secret-2026-0001";
const dockerVerification = process.env.STAYHUB_DOCKER_VERIFY === "true";
const adminEmail = process.env.STAYHUB_ADMIN_EMAIL || "admin@stayhub.local";
const adminPassword = process.env.STAYHUB_ADMIN_PASSWORD || "StayHubDemo2026!";
const adminMfaSecret = process.env.STAYHUB_ADMIN_MFA_SECRET || "";

function isoDate(days) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

async function availableRange(request, hotelId, start = 10) {
  for (let days = start; days <= 43; days += 2) {
    const checkIn = isoDate(days), checkOut = isoDate(days + 2);
    const response = await request.get(`api/inventario-tarifas/disponibilidad?hotelId=${hotelId}&desde=${checkIn}&hasta=${checkOut}`);
    expect(response.ok(), await response.text()).toBeTruthy();
    const standard = (await response.json()).find(item => item.tipoHabitacion === "STANDARD");
    if (standard?.unidadesDisponibles > 0) return { checkIn, checkOut };
  }
  throw new Error("No hay un rango STANDARD con cupo en los próximos 45 días");
}

function docker(...args) {
  return execFileSync("docker", ["compose", ...args], {
    cwd: repo,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024
  });
}

function dbScalar(sql) {
  return docker("exec", "-T", "postgres", "psql", "-U", "stayhub", "-d", "stayhub", "-tA", "-c", sql).trim();
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

async function login(request, email, password) {
  const response = await mutate(request, "POST", "api/usuarios/login", { email, password });
  const raw = await response.text();
  expect(response.status(), raw).toBe(200);
  const user = JSON.parse(raw);
  if (user.mfaRequerido) {
    expect(user.mfaConfigurado, "El administrador demo debe tener MFA activado").toBe(true);
    expect(adminMfaSecret, "Falta STAYHUB_ADMIN_MFA_SECRET en CI").not.toBe("");
    const verification = await mutate(request, "POST", "api/usuarios/mfa/verificar-login", {
      codigo: totp(adminMfaSecret)
    });
    expect(verification.status(), await verification.text()).toBe(200);
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
  const number = (hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return String(number).padStart(6, "0");
}

async function registerGuest(request, prefix) {
  const email = `${prefix}.${Date.now()}.${crypto.randomInt(1000, 9999)}@stayhub.local`;
  const password = "StayHubE2E.2026!";
  const response = await mutate(request, "POST", "api/usuarios", {
    email, password, nombre: "E2E", apellido: "Huésped", rol: "ADMIN"
  });
  expect(response.status(), await response.text()).toBe(201);
  expect((await response.json()).rol).toBe("HUESPED");
  await login(request, email, password);
  return { email, password };
}

async function firstHotel(request) {
  const response = await request.get("api/hoteles");
  expect(response.ok()).toBeTruthy();
  const hotels = await response.json();
  expect(hotels.length).toBeGreaterThan(0);
  return hotels[0];
}

async function availability(request, hotelId, from, to, roomType = "STANDARD") {
  const response = await request.get(`api/inventario-tarifas/disponibilidad?hotelId=${hotelId}&desde=${from}&hasta=${to}`);
  expect(response.ok(), await response.text()).toBeTruthy();
  const row = (await response.json()).find(item => item.tipoHabitacion === roomType);
  expect(row).toBeTruthy();
  return row.unidadesDisponibles;
}

function signWebhook(method, route, body) {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const nonce = crypto.randomUUID();
  const bodyHash = crypto.createHash("sha256").update(body).digest("hex");
  const canonical = `${timestamp}\n${nonce}\n${method}\n${route}\n${bodyHash}`;
  const signature = crypto.createHmac("sha256", webhookSecret).update(canonical).digest("hex");
  return { "X-StayHub-Timestamp": timestamp, "X-StayHub-Nonce": nonce,
    "X-StayHub-Signature": signature, "Content-Type": "application/json" };
}

async function expectLog(service, pattern) {
  await expect.poll(() => docker("logs", "--no-color", "--tail", "1000", service), { timeout: 30_000 }).toContain(pattern);
}

test.describe("StayHub — cinco recorridos críticos", () => {
  test("registro, login, carrito, reserva y pago persisten el resultado", async ({ request }) => {
    const email = `e2e.compra.${Date.now()}@stayhub.local`;
    const password = "StayHubE2E.2026!";
    const registration = await mutate(request, "POST", "api/usuarios", {
      email, password, nombre: "E2E", apellido: "Compra", rol: "ADMIN"
    });
    expect(registration.status(), await registration.text()).toBe(201);
    const user = await registration.json();
    expect(user.rol).toBe("HUESPED");
    expect(user.passwordHash).toBeUndefined();
    await login(request, email, password);
    const hotel = await firstHotel(request);
    const { checkIn, checkOut } = await availableRange(request, hotel.id, 10);
    expect((await mutate(request, "POST", "api/carrito/hotel", {
      hotelId: hotel.id, tipoHabitacion: "STANDARD", cantidadHabitaciones: 1
    })).status()).toBe(200);
    expect((await mutate(request, "POST", "api/carrito/fechas", { checkIn, checkOut })).status()).toBe(200);
    const guestStep = await mutate(request, "POST", "api/carrito/huesped", {
      huespedNombre: "Manipulado", huespedApellido: "Manipulado", huespedEmail: "otro@invalid.test",
      huespedTelefono: "1155550000", precioTotal: 1, moneda: "USD"
    });
    expect(guestStep.status(), await guestStep.text()).toBe(200);
    const cart = await guestStep.json();
    expect(cart.listoParaConfirmar).toBeTruthy();
    expect(cart.huespedEmail).toBe(email);
    const bookingResponse = await mutate(request, "POST", "api/carrito/confirmar", {});
    expect(bookingResponse.status(), await bookingResponse.text()).toBe(200);
    const booking = await bookingResponse.json();
    expect(booking.estado).toBe("PENDIENTE");
    const paymentKey = `e2e-payment-${booking.id}-${Date.now()}`;
    const paymentBody = {
      reservaId: booking.id, monto: booking.precioTotal, moneda: booking.moneda,
      claveIdempotencia: paymentKey
    };
    const paymentResponse = await mutate(request, "POST", "api/pagos", paymentBody,
      { "Idempotency-Key": paymentKey });
    expect(paymentResponse.status(), await paymentResponse.text()).toBe(201);
    const payment = await paymentResponse.json();
    expect(payment.estado).toBe("APROBADO");
    const repeatedPayment = await mutate(request, "POST", "api/pagos", paymentBody,
      { "Idempotency-Key": paymentKey });
    expect(repeatedPayment.status(), await repeatedPayment.text()).toBe(201);
    expect((await repeatedPayment.json()).id).toBe(payment.id);
    const persistedBooking = await request.get(`api/reservas/${booking.id}`);
    expect((await persistedBooking.json()).estado).toBe("CONFIRMADA");
    const persistedPayment = await request.get(`api/pagos/${payment.id}`);
    expect(persistedPayment.status(), await persistedPayment.text()).toBe(200);
    expect((await persistedPayment.json()).estado).toBe("APROBADO");
    if (dockerVerification) {
      expect(dbScalar(`SELECT estado FROM reserva WHERE id=${Number(booking.id)}`)).toBe("CONFIRMADA");
      expect(dbScalar(`SELECT estado FROM pago WHERE id=${Number(payment.id)}`)).toBe("APROBADO");
    }
  });

  test("cancelar una reserva devuelve el cupo al inventario", async ({ request }) => {
    const guest = await registerGuest(request, "e2e.cancelacion");
    const hotel = await firstHotel(request);
    const { checkIn, checkOut } = await availableRange(request, hotel.id, 12);
    const before = await availability(request, hotel.id, checkIn, checkOut);
    const bookingKey = `e2e-booking-${Date.now()}`;
    const bookingBody = {
      hotelId: hotel.id, tipoHabitacion: "STANDARD", cantidadHabitaciones: 1,
      checkIn, checkOut, huespedNombre: "Demo", huespedApellido: "Huésped",
      huespedEmail: guest.email, huespedTelefono: "1155550001",
      precioTotal: 1, moneda: "USD"
    };
    const creation = await mutate(request, "POST", "api/reservas", bookingBody,
      { "Idempotency-Key": bookingKey });
    expect(creation.status(), await creation.text()).toBe(201);
    const booking = await creation.json();
    const repeatedCreation = await mutate(request, "POST", "api/reservas", bookingBody,
      { "Idempotency-Key": bookingKey });
    expect(repeatedCreation.status(), await repeatedCreation.text()).toBe(201);
    expect((await repeatedCreation.json()).id).toBe(booking.id);
    expect(await availability(request, hotel.id, checkIn, checkOut)).toBe(before - 1);
    const cancellation = await mutate(request, "DELETE", `api/reservas/${booking.id}`);
    expect(cancellation.status(), await cancellation.text()).toBe(200);
    expect((await cancellation.json()).estado).toBe("CANCELADA");
    expect(await availability(request, hotel.id, checkIn, checkOut)).toBe(before);
    const persistedCancellation = await request.get(`api/reservas/${booking.id}`);
    expect((await persistedCancellation.json()).estado).toBe("CANCELADA");
    if (dockerVerification) {
      expect(dbScalar(`SELECT estado FROM reserva WHERE id=${Number(booking.id)}`)).toBe("CANCELADA");
    }
  });

  test("una reserva OTA firmada se procesa una sola vez y queda confirmada", async ({ request }) => {
    const hotel = await firstHotel(request);
    const externalId = `e2e-ota-${Date.now()}`;
    const route = "canales-externos/otas/BOOKING/reservas";
    const { checkIn, checkOut } = await availableRange(request, hotel.id, 14);
    const body = JSON.stringify({ idExterno: externalId, canal: "BOOKING", hotelId: hotel.id,
      checkIn, checkOut, tipoHabitacion: "STANDARD", cantidadHabitaciones: 1,
      huesped: { nombre: "E2E", apellido: "OTA", email: "e2e.ota@stayhub.local", telefono: "1155550002" },
      precioTotal: 100000, moneda: "ARS", estado: "CONFIRMADA" });
    const response = await request.post(`api/${route}`, { data: body, headers: signWebhook("POST", route, body) });
    expect(response.status(), await response.text()).toBe(201);
    const result = await response.json();
    expect(result.idExterno).toBe(externalId);
    expect(result.estado).toBe("CONFIRMADA");
    const updateBody = JSON.stringify({ ...JSON.parse(body), precioTotal: 110000 });
    const updateRoute = `${route}/${externalId}`;
    const update = await request.put(`api/${updateRoute}`, {
      data: updateBody,
      headers: signWebhook("PUT", updateRoute, updateBody)
    });
    expect(update.status(), await update.text()).toBe(200);
    expect((await update.json()).reservaId).toBe(result.reservaId);
    if (dockerVerification) {
      expect(dbScalar(`SELECT canal || ':' || estado FROM reserva WHERE id=${Number(result.reservaId)}`)).toBe("BOOKING:CONFIRMADA");
      expect(dbScalar(`SELECT count(*) FROM reserva WHERE canal='BOOKING' AND referencia_externa='${externalId}'`)).toBe("1");
    }
  });

  test("la sincronización JMS llega a los mocks OTA y PMS", async ({ request }) => {
    await login(request, adminEmail, adminPassword);
    const hotel = await firstHotel(request);
    const from = isoDate(10), to = isoDate(12);
    const ota = await mutate(request, "POST", `api/canales-externos/otas/BOOKING/sincronizaciones?hotelId=${hotel.id}&desde=${from}&hasta=${to}`, {});
    const pms = await mutate(request, "POST", `api/canales-externos/pms/sincronizaciones?hotelId=${hotel.id}&desde=${from}&hasta=${to}`, {});
    expect(ota.status(), await ota.text()).toBe(202);
    expect(pms.status(), await pms.text()).toBe(202);
    expect((await ota.json()).solicitudId).toMatch(/^[0-9a-f-]{36}$/);
    expect((await pms.json()).solicitudId).toMatch(/^[0-9a-f-]{36}$/);
    if (dockerVerification) {
      await expectLog("ota-mock", "[OTA] Sincronización recibida");
      await expectLog("pms-mock", `[PMS] Sincronización recibida para hotel ${hotel.id}`);
    }
  });

  test("overbooking reubica, persiste el conflicto y publica la notificación", async ({ request }) => {
    await login(request, adminEmail, adminPassword);
    const hotel = await firstHotel(request);
    const response = await mutate(request, "POST", "api/overbooking/resolver", {
      reservaIdConflictiva: 900000 + Date.now() % 100000, hotelId: hotel.id,
      tipoHabitacion: "STANDARD", cantidadHabitaciones: 1,
      checkIn: isoDate(35), checkOut: isoDate(37), canalOrigen: "BOOKING",
      referenciaExterna: `e2e-ovb-${Date.now()}`, huespedEmail: "e2e.overbooking@stayhub.local"
    });
    expect(response.status(), await response.text()).toBe(200);
    const result = await response.json();
    expect(result.resuelto).toBeTruthy();
    expect(result.estrategia).toBe("REUBICACION");
    expect(result.tipoHabitacionAlternativa).toBe("SUITE");
    if (dockerVerification) {
      expect(dbScalar(`SELECT estrategia_aplicada || ':' || resuelto FROM conflicto_overbooking WHERE id=${Number(result.conflictoId)}`)).toBe("REUBICACION:true");
      await expect.poll(() => docker("logs", "--no-color", "--tail", "1000", "app"), { timeout: 30_000 }).toMatch(/overbooking|notificaci[oó]n/i);
    }
  });
});
