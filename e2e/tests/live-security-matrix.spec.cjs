const { test, expect, request: apiRequest } = require("@playwright/test");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const baseURL = `${process.env.STAYHUB_BASE_URL || "https://localhost:8443/StayHub"}/`.replace(/\/+$/, "/");
const origin = new URL(baseURL).origin;

async function client() {
  return apiRequest.newContext({
    baseURL,
    ignoreHTTPSErrors: true,
    extraHTTPHeaders: { Accept: "application/json" }
  });
}

async function csrf(api) {
  const response = await api.get("api/seguridad/csrf");
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).token;
}

async function mutate(api, method, url, data) {
  return api.fetch(url, {
    method,
    data,
    headers: {
      Origin: origin,
      "X-CSRF-Token": await csrf(api),
      "Content-Type": "application/json"
    }
  });
}

async function register(api, prefix, extra = {}) {
  const email = `${prefix}.${Date.now()}.${crypto.randomInt(1000, 9999)}@stayhub.local`;
  const password = "StayHubSecurity.2026!";
  const response = await mutate(api, "POST", "api/usuarios", {
    email, password, nombre: "Prueba", apellido: "Seguridad", rol: "ADMIN", ...extra
  });
  expect(response.status(), await response.text()).toBe(201);
  return { ...(await response.json()), email, password };
}

async function login(api, user) {
  const response = await mutate(api, "POST", "api/usuarios/login", {
    email: user.email, password: user.password
  });
  expect(response.status(), await response.text()).toBe(200);
}

test.describe.serial("KAN-100 — matriz de seguridad en vivo", () => {
  let apiA;
  let apiB;
  let userA;
  let userB;

  test.beforeAll(async () => {
    apiA = await client();
    apiB = await client();
    userA = await register(apiA, "security.a");
    userB = await register(apiB, "security.b");
    await login(apiA, userA);
    await login(apiB, userB);
  });

  test.afterAll(async () => {
    await apiA?.dispose();
    await apiB?.dispose();
  });

  test("mass assignment: registrarse nunca concede ADMIN", async () => {
    expect(userA.rol).toBe("HUESPED");
    expect(userB.rol).toBe("HUESPED");
    expect(userA.passwordHash).toBeUndefined();
  });

  test("BOLA/IDOR: un usuario no puede consultar el perfil de otro", async () => {
    expect((await apiA.get(`api/usuarios/${userA.id}`)).status()).toBe(200);
    const foreign = await apiA.get(`api/usuarios/${userB.id}`);
    expect([403, 404]).toContain(foreign.status());
  });

  test("BFLA: un huésped no puede ejecutar una operación ADMIN", async () => {
    const response = await mutate(apiA, "POST", "api/hoteles", {
      nombre: "No autorizado", direccion: "X", ciudad: "X", pais: "AR",
      descripcion: "X", servicios: []
    });
    expect(response.status(), await response.text()).toBe(403);
  });

  test("inyección SQL en un identificador se rechaza como entrada inválida", async () => {
    const response = await apiA.get("api/hoteles/1%20OR%201%3D1");
    expect([400, 404]).toContain(response.status());
    expect(response.headers()["content-type"]).toContain("application/json");
  });

  test("XSS: toda escritura HTML dinámica pasa por el sanitizador local", async () => {
    const jsRoot = path.resolve(__dirname, "../../proyecto/src/main/webapp/assets/js");
    const walk = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry =>
      entry.isDirectory() ? walk(path.join(directory, entry.name)) : [path.join(directory, entry.name)]);
    const applicationFiles = walk(jsRoot).filter(file => file.endsWith(".js")
      && !file.includes(`${path.sep}vendor${path.sep}`) && !file.endsWith("dom-seguro.js"));
    const unsafeWrites = [];
    for (const file of applicationFiles) {
      const source = fs.readFileSync(file, "utf8");
      for (const match of source.matchAll(/innerHTML\s*=/g)) {
        if (!source.slice(match.index, match.index + 220).includes("StayHubDOM.html")) {
          unsafeWrites.push(`${path.relative(jsRoot, file)}:${source.slice(0, match.index).split("\n").length}`);
        }
      }
    }
    const sanitizer = fs.readFileSync(path.join(jsRoot, "dom-seguro.js"), "utf8");
    expect(sanitizer).toContain("DOMPurify.sanitize");
    expect(sanitizer).toContain("ALLOW_UNKNOWN_PROTOCOLS: false");
    expect(unsafeWrites).toEqual([]);
  });

  test("fuerza bruta: el límite de login responde 429 con Retry-After", async () => {
    const token = await csrf(apiA);
    let last;
    for (let attempt = 0; attempt < 61; attempt += 1) {
      last = await apiA.post("api/usuarios/login", {
        data: { email: "cuenta.inexistente@stayhub.local", password: "CredencialIncorrecta.2026!" },
        headers: { Origin: origin, "X-CSRF-Token": token, "Content-Type": "application/json" }
      });
    }
    expect(last.status(), await last.text()).toBe(429);
    expect(last.headers()["retry-after"]).toBe("900");
  });
});
