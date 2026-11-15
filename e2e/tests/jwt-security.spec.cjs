// Ataques al JWT probados por HTTP contra la aplicación desplegada (Eje D, Clase 15):
// "JWT vencido, con firma alterada, con alg=none o de otro emisor → 401".
// El vencimiento se prueba en TokenJwtTest (acá no se puede esperar 15 minutos).
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

async function registrarYLoguear(client) {
  const email = `e2e.jwt.${Date.now()}.${crypto.randomInt(1000, 9999)}@stayhub.local`;
  const password = "StayHubJwt.2026!";
  const alta = await mutate(client, "POST", "api/usuarios", { email, password, nombre: "Prueba", apellido: "Jwt" });
  expect(alta.status(), await alta.text()).toBe(201);
  const login = await mutate(client, "POST", "api/usuarios/login", { email, password });
  expect(login.status(), await login.text()).toBe(200);
}

async function tokenDe(client) {
  const cookie = (await client.storageState()).cookies.find(c => c.name === "STAYHUB_TOKEN");
  expect(cookie, "el login tiene que dejar la cookie STAYHUB_TOKEN").toBeTruthy();
  return cookie.value;
}

const b64url = valor => Buffer.from(typeof valor === "string" ? valor : JSON.stringify(valor)).toString("base64url");
const leerParte = parte => JSON.parse(Buffer.from(parte, "base64url").toString("utf8"));

async function conBearer(token, ruta = "api/reservas/mias") {
  const client = await context({ Authorization: `Bearer ${token}` });
  try {
    const response = await client.get(ruta);
    return { status: response.status(), body: await response.json().catch(() => ({})) };
  } finally {
    await client.dispose();
  }
}

async function esperarTokenInvalido(token, ruta) {
  const { status, body } = await conBearer(token, ruta);
  expect(status).toBe(401);
  expect(body.codigo).toBe("TOKEN_INVALIDO");
}

test.describe.serial("Seguridad del JWT por HTTP", () => {
  let navegador;
  let token;

  test.beforeAll(async () => {
    navegador = await context();
    await registrarYLoguear(navegador);
    token = await tokenDe(navegador);
  });

  test.afterAll(async () => {
    await navegador?.dispose();
  });

  test("el login entrega el JWT en una cookie HttpOnly y SameSite=Lax", async () => {
    const cookie = (await navegador.storageState()).cookies.find(c => c.name === "STAYHUB_TOKEN");
    expect(cookie.httpOnly).toBeTruthy();
    expect(cookie.sameSite).toBe("Lax");
    expect(token.split(".")).toHaveLength(3);
    expect(leerParte(token.split(".")[0]).alg).toBe("HS256");
  });

  test("un JWT válido permite el acceso (cookie y Bearer)", async () => {
    expect((await navegador.get("api/reservas/mias")).status()).toBe(200);
    expect((await conBearer(token)).status).toBe(200);
  });

  test("JWT con la firma alterada → 401", async () => {
    const [cabecera, datos, firma] = token.split(".");
    const otraFirma = firma.slice(0, -2) + (firma.endsWith("AA") ? "BB" : "AA");
    await esperarTokenInvalido(`${cabecera}.${datos}.${otraFirma}`);
  });

  test("JWT con el payload alterado para ser ADMIN → 401", async () => {
    const [cabecera, datos, firma] = token.split(".");
    const admin = b64url({ ...leerParte(datos), rol: "ADMIN" });
    await esperarTokenInvalido(`${cabecera}.${admin}.${firma}`, "api/reservas");
  });

  test("JWT con alg=none → 401", async () => {
    const datos = token.split(".")[1];
    const none = b64url({ alg: "none", typ: "JWT" });
    await esperarTokenInvalido(`${none}.${datos}.`);
    await esperarTokenInvalido(`${none}.${b64url({ ...leerParte(datos), rol: "ADMIN" })}.x`, "api/reservas");
  });

  test("JWT de otro emisor, firmado con otra clave → 401", async () => {
    const datos = leerParte(token.split(".")[1]);
    const cuerpo = `${b64url({ alg: "HS256", typ: "JWT" })}.${b64url({ ...datos, iss: "https://otro-emisor.example" })}`;
    const firma = crypto.createHmac("sha256", crypto.randomBytes(32)).update(cuerpo).digest("base64url");
    await esperarTokenInvalido(`${cuerpo}.${firma}`);
  });

  test("un header Authorization inválido no desactiva la protección CSRF", async () => {
    for (const authorization of ["Bearer basura", "Basic dXN1YXJpbzpjbGF2ZQ=="]) {
      const client = await context({ Authorization: authorization });
      try {
        // Sin token CSRF: si el header inválido apagara el control, el pedido llegaría al login.
        const response = await client.post("api/usuarios/login", {
          data: { email: "nadie@stayhub.local", password: "x" },
          headers: { "Content-Type": "application/json", Origin: new URL(baseURL).origin }
        });
        expect(response.status(), `${authorization} -> ${await response.text()}`).toBe(403);
      } finally {
        await client.dispose();
      }
    }
  });

  test("un JWT copiado no sirve después del logout (replay → 401)", async () => {
    const copiado = await tokenDe(navegador);
    expect((await conBearer(copiado)).status).toBe(200);

    const logout = await mutate(navegador, "DELETE", "api/usuarios/sesion");
    expect(logout.status(), await logout.text()).toBe(204);

    await esperarTokenInvalido(copiado);
    const conCookie = await context({ Cookie: `STAYHUB_TOKEN=${copiado}` });
    try {
      expect((await conCookie.get("api/reservas/mias")).status()).toBe(401);
    } finally {
      await conCookie.dispose();
    }
  });
});
