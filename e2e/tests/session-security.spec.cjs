const { test, expect, request: apiRequest } = require("@playwright/test");
const crypto = require("node:crypto");

const baseURL = `${process.env.STAYHUB_BASE_URL || "https://localhost:8443/StayHub"}/`.replace(/\/+$/, "/");

async function context(headers = {}) {
  return apiRequest.newContext({
    baseURL,
    ignoreHTTPSErrors: true,
    extraHTTPHeaders: { Accept: "application/json", ...headers }
  });
}

async function csrf(client) {
  const response = await client.get("api/seguridad/csrf");
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).token;
}

async function mutate(client, method, url, data) {
  const token = await csrf(client);
  return client.fetch(url, {
    method,
    data,
    headers: { Origin: new URL(baseURL).origin, "X-CSRF-Token": token, "Content-Type": "application/json" }
  });
}

async function registrar(client, prefijo) {
  const email = `${prefijo}.${Date.now()}.${crypto.randomInt(1000, 9999)}@stayhub.local`;
  const password = "StayHubSesion.2026!";
  const response = await mutate(client, "POST", "api/usuarios", {
    email, password, nombre: "Prueba", apellido: "Sesión", rol: "ADMIN"
  });
  expect(response.status(), await response.text()).toBe(201);
  return { ...(await response.json()), email, password };
}

async function login(client, usuario) {
  const response = await mutate(client, "POST", "api/usuarios/login", {
    email: usuario.email,
    password: usuario.password
  });
  expect(response.status(), await response.text()).toBe(200);
}

function sessionCookie(storageState) {
  return storageState.cookies.find(cookie => cookie.name === "JSESSIONID");
}

test.describe.serial("Seguridad de sesiones HTTP", () => {
  let usuarioA;
  let usuarioB;
  let datosA;
  let datosB;
  let cookieAntesDelLoginB;

  test.beforeAll(async () => {
    usuarioA = await context();
    usuarioB = await context();
    datosA = await registrar(usuarioA, "e2e.usuario-a");
    datosB = await registrar(usuarioB, "e2e.usuario-b");
    cookieAntesDelLoginB = sessionCookie(await usuarioB.storageState());
    await login(usuarioA, datosA);
    await login(usuarioB, datosB);
  });

  test.afterAll(async () => {
    await usuarioA?.dispose();
    await usuarioB?.dispose();
  });

  test("una cookie de sesión manipulada no autentica al cliente", async () => {
    const client = await context({ Cookie: "JSESSIONID=valor-manipulado" });
    try {
      const response = await client.get("api/reservas/mias");
      expect(response.status()).toBe(401);
      expect((await response.json()).codigo).toBe("NO_AUTENTICADO");
    } finally {
      await client.dispose();
    }
  });

  test("el login reemplaza el identificador de sesión previo", async () => {
    const despues = sessionCookie(await usuarioB.storageState());
    expect(cookieAntesDelLoginB).toBeTruthy();
    expect(despues).toBeTruthy();
    expect(despues.value).not.toBe(cookieAntesDelLoginB.value);
    expect(despues.httpOnly).toBeTruthy();
    expect(despues.secure).toBeTruthy();
    expect(despues.sameSite).toBe("Lax");
  });

  test("la sesión de un usuario no permite consultar el perfil de otro", async () => {
    expect((await usuarioA.get(`api/usuarios/${datosA.id}`)).status()).toBe(200);
    expect([403, 404]).toContain((await usuarioA.get(`api/usuarios/${datosB.id}`)).status());
  });

  test("el logout invalida la sesión y evita su reutilización", async () => {
    expect((await usuarioA.get("api/reservas/mias")).status()).toBe(200);

    const logout = await mutate(usuarioA, "DELETE", "api/usuarios/sesion");
    expect(logout.status(), await logout.text()).toBe(204);
    expect((await usuarioA.get("api/reservas/mias")).status()).toBe(401);
  });
});
