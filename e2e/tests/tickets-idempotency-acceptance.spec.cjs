const { test, expect, request: apiRequest } = require('@playwright/test');
const crypto = require('node:crypto');

const baseURL = `${process.env.STAYHUB_BASE_URL || 'https://localhost:8080/StayHub'}/`;
const origin = new URL(baseURL).origin;
const run = crypto.randomUUID();
const sharedKey = `qa-shared-${run}`;
const batchKey = `qa-batch-${run}`;
let a, b, payload, bookingA, bookingB, paymentA, batchReservations;

async function client(label) {
  const api = await apiRequest.newContext({ baseURL, ignoreHTTPSErrors: true });
  const csrf = await api.get('api/seguridad/csrf');
  let token = (await csrf.json()).token;
  const email = `qa.${label}.${run}@stayhub.invalid`;
  const password = 'StayHubAceptacion.2026!';
  const registration = await api.post('api/usuarios', {
    data: { email, password, nombre: 'QA', apellido: label, rol: 'HUESPED' },
    headers: { Origin: origin, 'X-CSRF-Token': token }
  });
  expect(registration.status(), await registration.text()).toBe(201);
  const login = await api.post('api/usuarios/login', {
    data: { email, password }, headers: { Origin: origin, 'X-CSRF-Token': token }
  });
  expect(login.status(), await login.text()).toBe(200);
  token = (await (await api.get('api/seguridad/csrf')).json()).token;
  return { api, token, email };
}

async function post(user, path, data, key) {
  return user.api.post(path, { data,
    headers: { Origin: origin, 'X-CSRF-Token': user.token, 'Idempotency-Key': key }
  });
}

async function created(response) {
  expect(response.status(), await response.text()).toBe(201);
  return response.json();
}

async function freeRange(api) {
  const hotels = await (await api.get('api/hoteles?limite=20')).json();
  for (const hotel of hotels.slice(0, 3)) {
    for (let days = 12; days < 42; days += 3) {
      const start = new Date(); start.setUTCDate(start.getUTCDate() + days);
      const end = new Date(start); end.setUTCDate(end.getUTCDate() + 2);
      const checkIn = start.toISOString().slice(0, 10), checkOut = end.toISOString().slice(0, 10);
      const response = await api.get(`api/inventario-tarifas/disponibilidad?hotelId=${hotel.id}&desde=${checkIn}&hasta=${checkOut}`);
      expect(response.ok(), await response.text()).toBeTruthy();
      const room = (await response.json()).find(r => r.unidadesDisponibles >= 4);
      if (room) return { hotelId: hotel.id, tipoHabitacion: room.tipoHabitacion,
        cantidadHabitaciones: 1, checkIn, checkOut, huespedTelefono: '1100000000' };
    }
  }
  throw new Error('El fixture no tiene cuatro unidades en un rango de prueba');
}

test.describe.serial('KAN-95 — aceptación de idempotencia en HTTP real', () => {
  test.setTimeout(180_000);
  test.beforeAll(async () => {
    // La autenticación conserva las comprobaciones persistentes de development.
    test.setTimeout(180_000);
    a = await client('A'); b = await client('B');
    payload = await freeRange(a.api);
  });
  test.afterAll(async () => { await a?.api.dispose(); await b?.api.dispose(); });

  test('reserva: concurrencia y repetición conservan cuerpo, ubicación y un recurso por usuario', async () => {
    const responses = await Promise.all(Array.from({ length: 3 }, () => post(a, 'api/reservas', payload, sharedKey)));
    const results = await Promise.all(responses.map(created));
    bookingA = results[0];
    expect(bookingA.estado).toBe('PENDIENTE');
    for (const result of results) expect(result).toEqual(bookingA);
    for (const response of responses) expect(response.headers().location).toBe(responses[0].headers().location);
    expect(await created(await post(a, 'api/reservas', payload, sharedKey))).toEqual(bookingA);
    bookingB = await created(await post(b, 'api/reservas', payload, sharedKey));
    expect(bookingB.id).not.toBe(bookingA.id);
    expect(bookingA.huespedEmail).toBe(a.email);
    expect(bookingB.huespedEmail).toBe(b.email);
    const mine = await (await a.api.get('api/reservas/mias')).json();
    expect(mine.filter(r => r.id === bookingA.id)).toHaveLength(1);
    expect([403, 404]).toContain((await b.api.get(`api/reservas/${bookingA.id}`)).status());
  });

  test('pago: concurrencia no duplica cobro y la misma clave se aísla por usuario y de reservas', async () => {
    const body = { reservaId: bookingA.id, monto: bookingA.precioTotal, moneda: bookingA.moneda };
    const responses = await Promise.all(Array.from({ length: 3 }, () => post(a, 'api/pagos', body, sharedKey)));
    const results = await Promise.all(responses.map(created));
    paymentA = results[0];
    expect(paymentA.estado).toBe('APROBADO');
    for (const result of results) expect(result).toEqual(paymentA);
    for (const response of responses) expect(response.headers().location).toBe(responses[0].headers().location);
    expect(await created(await post(a, 'api/pagos', body, sharedKey))).toEqual(paymentA);
    const paymentB = await created(await post(b, 'api/pagos', {
      reservaId: bookingB.id, monto: bookingB.precioTotal, moneda: bookingB.moneda
    }, sharedKey));
    expect(paymentB.id).not.toBe(paymentA.id);
    const mine = await (await a.api.get('api/pagos/mios')).json();
    expect(mine.filter(p => p.id === paymentA.id)).toHaveLength(1);
    expect([403, 404]).toContain((await b.api.get(`api/pagos/${paymentA.id}`)).status());
  });

  test('reutilizar una clave de pago para otra operación se rechaza sin devolver un pago ajeno al pedido', async () => {
    batchReservations = [];
    for (let i = 0; i < 2; i++) batchReservations.push(await created(await post(a, 'api/reservas', payload, `qa-batch-reserva-${i}-${run}`)));
    const body = { reservaIds: batchReservations.map(r => r.id),
      monto: batchReservations.reduce((sum, r) => sum + r.precioTotal, 0), moneda: batchReservations[0].moneda };
    const response = await post(a, 'api/pagos/lote', body, sharedKey);
    expect(response.status(), await response.text()).toBe(409);
    for (const reservation of batchReservations) {
      expect((await (await a.api.get(`api/reservas/${reservation.id}`)).json()).estado).toBe('PENDIENTE');
    }
  });

  test('pago por lote: repetición concurrente devuelve el mismo resultado completo y confirma cada reserva una vez', async () => {
    const body = { reservaIds: batchReservations.map(r => r.id),
      monto: batchReservations.reduce((sum, r) => sum + r.precioTotal, 0), moneda: batchReservations[0].moneda };
    const responses = await Promise.all(Array.from({ length: 3 }, () => post(a, 'api/pagos/lote', body, batchKey)));
    const results = await Promise.all(responses.map(created));
    expect(results[0].estado).toBe('APROBADO');
    for (const result of results) expect(result).toEqual(results[0]);
    expect(results[0].reservaIds.slice().sort()).toEqual(body.reservaIds.slice().sort());
    for (const reservation of batchReservations) {
      expect((await (await a.api.get(`api/reservas/${reservation.id}`)).json()).estado).toBe('CONFIRMADA');
    }
  });
});
