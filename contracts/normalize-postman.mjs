import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const postmanDir = path.join(root, "postman");
const baseUrl = "{{baseUrl}}";

const expectedByCollection = {
  "StayHub-ServicioDeNotificaciones.postman_collection.json": [200, 200, 200, 400, 400, 400],
  "StayHub-ServicioDeOverbooking.postman_collection.json": [201, 200, 200, 400, 400, 400, 400, 400],
  "StayHub-ServicioDePagos.postman_collection.json": [201, 400, 200],
  "StayHub-ServicioDeUsuarios.postman_collection.json": [201, 201, 200, 200]
};

function requests(items, result = []) {
  for (const item of items ?? []) {
    if (item.request) result.push(item);
    requests(item.item, result);
  }
  return result;
}

function testEvent(lines) {
  return [{ listen: "test", script: { type: "text/javascript", exec: lines } }];
}

function csrfRequest() {
  return {
    name: "Obtener CSRF",
    request: { method: "GET", header: [], url: `${baseUrl}/seguridad/csrf` },
    event: testEvent([
      "pm.test('CSRF disponible', () => pm.response.to.have.status(200));",
      "pm.collectionVariables.set('csrfToken', pm.response.json().token);"
    ])
  };
}

function minimumRequest(file) {
  const commonHeaders = [
    { key: "Origin", value: "https://localhost:8443" },
    { key: "X-CSRF-Token", value: "{{csrfToken}}" },
    { key: "Content-Type", value: "application/json" }
  ];
  if (file.includes("Usuarios")) return {
    name: "Login demo válido",
    request: { method: "POST", header: commonHeaders, url: `${baseUrl}/usuarios/login`,
      body: { mode: "raw", raw: JSON.stringify({ email: "huesped@stayhub.local", password: "StayHubDemo2026!" }) } },
    event: testEvent(["pm.test('Login demo responde 200', () => pm.response.to.have.status(200));"])
  };
  if (file.includes("Pagos")) return {
    name: "Pagos exige autenticación",
    request: { method: "GET", header: [], url: `${baseUrl}/pagos/mios` },
    event: testEvent(["pm.test('Pagos rechaza sesión anónima', () => pm.response.to.have.status(401));"])
  };
  if (file.includes("Notificaciones")) return {
    name: "Notificaciones exige ADMIN",
    request: { method: "POST", header: commonHeaders, url: `${baseUrl}/notificaciones/enviar`,
      body: { mode: "raw", raw: JSON.stringify({ destinatario: "qa@example.test", canalPreferido: "EMAIL", tipoEvento: "PRUEBA", mensaje: "Prueba CI" }) } },
    event: testEvent(["pm.test('Notificaciones rechaza sesión anónima', () => pm.response.to.have.status(401));"])
  };
  if (file.includes("Overbooking")) return {
    name: "Overbooking exige ADMIN",
    request: { method: "POST", header: commonHeaders, url: `${baseUrl}/overbooking/resolver`,
      body: { mode: "raw", raw: JSON.stringify({ hotelId: 1, tipoHabitacion: "STANDARD", cantidadHabitaciones: 1, checkIn: "2027-01-10", checkOut: "2027-01-12", reservaIdConflictiva: 999999 }) } },
    event: testEvent(["pm.test('Overbooking rechaza sesión anónima', () => pm.response.to.have.status(401));"])
  };
  if (file.includes("Hoteles")) return {
    name: "Catálogo público de hoteles",
    request: { method: "GET", header: [], url: `${baseUrl}/hoteles` },
    event: testEvent(["pm.test('Hoteles responde 200', () => pm.response.to.have.status(200));"])
  };
  if (file.includes("Inventario")) return {
    name: "Disponibilidad pública",
    request: { method: "GET", header: [], url: `${baseUrl}/inventario-tarifas/disponibilidad?hotelId=1&desde=2027-01-10&hasta=2027-01-12` },
    event: testEvent(["pm.test('Disponibilidad responde 200', () => pm.response.to.have.status(200));"])
  };
  if (file.includes("Reservas")) return {
    name: "Reservas exige autenticación",
    request: { method: "GET", header: [], url: `${baseUrl}/reservas/mias` },
    event: testEvent(["pm.test('Reservas rechaza sesión anónima', () => pm.response.to.have.status(401));"])
  };
  return {
    name: "Catálogo multicanal público",
    request: { method: "GET", header: [], url: `${baseUrl}/catalogo-canales/ofertas` },
    event: testEvent(["pm.test('Catálogo multicanal responde 200', () => pm.response.to.have.status(200));"])
  };
}

for (const file of fs.readdirSync(postmanDir).filter(name => name.endsWith(".postman_collection.json"))) {
  if (file.includes("IntegracionPmsSOAP")) continue;
  const target = path.join(postmanDir, file);
  const collection = JSON.parse(fs.readFileSync(target, "utf8"));
  collection.variable ??= [];
  const configuredBase = collection.variable.find(variable => variable.key === "baseUrl");
  if (configuredBase) configuredBase.value = "https://localhost:8443/StayHub/api";
  else collection.variable.push({ key: "baseUrl", value: "https://localhost:8443/StayHub/api" });

  for (const item of requests(collection.item)) {
    if (typeof item.request.url === "string") item.request.url = item.request.url.replace(/https?:\/\/localhost:(?:8080|8443)\/StayHub\/api/g, baseUrl);
    else if (item.request.url?.raw) item.request.url.raw = item.request.url.raw.replace(/https?:\/\/localhost:(?:8080|8443)\/StayHub\/api/g, baseUrl);
  }

  const expected = expectedByCollection[file];
  if (expected) requests(collection.item).forEach((item, index) => {
    if (!(item.event ?? []).some(event => event.listen === "test")) {
      item.event = testEvent([
        `pm.test('HTTP ${expected[index]} esperado', () => pm.response.to.have.status(${expected[index]}));`,
        "pm.test('Sin error interno expuesto', () => pm.expect(pm.response.text()).not.to.include('java.'));"
      ]);
    }
  });

  collection.item = (collection.item ?? []).filter(item => item.name !== "CI - Contrato mínimo");
  collection.item.unshift({ name: "CI - Contrato mínimo", item: [csrfRequest(), minimumRequest(file)] });
  fs.writeFileSync(target, `${JSON.stringify(collection, null, 2)}\n`);
}

console.log("Colecciones Postman normalizadas para ejecución local y CI.");
