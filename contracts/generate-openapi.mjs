import { promises as fs } from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const javaRoot = path.join(root, "proyecto", "src", "main", "java");
const output = path.join(root, "contracts", "openapi", "stayhub-openapi.json");

async function files(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const nested = await Promise.all(entries.map((entry) => {
    const full = path.join(dir, entry.name);
    return entry.isDirectory() ? files(full) : [full];
  }));
  return nested.flat();
}

function normalisePath(value) {
  const joined = `/api/${value}`.replaceAll(/\/+/g, "/");
  return joined.length > 1 && joined.endsWith("/") ? joined.slice(0, -1) : joined;
}

function discover(source, file) {
  const classIndex = source.search(/public\s+class\s+\w+/);
  if (classIndex < 0) return [];
  const classPrefix = source.slice(0, classIndex);
  const classPaths = [...classPrefix.matchAll(/@Path\("([^"]+)"\)/g)];
  if (!classPaths.length) return [];
  const base = classPaths.at(-1)[1];
  const endpoints = [];
  const method = /((?:\s*@[^\n]+\n)+)\s*public\s+(?:[\w<>?,.\[\]\s]+)\s+(\w+)\s*\(/g;
  for (const match of source.slice(classIndex).matchAll(method)) {
    const annotations = match[1];
    const verb = annotations.match(/@(GET|POST|PUT|PATCH|DELETE)\b/);
    if (!verb) continue;
    const subPath = annotations.match(/@Path\("([^"]+)"\)/)?.[1] ?? "";
    endpoints.push({
      method: verb[1].toLowerCase(),
      path: normalisePath(`${base}/${subPath}`),
      operationId: `${path.basename(file, ".java").replace(/Resource$/, "")}_${match[2]}`,
      source: path.relative(root, file).replaceAll("\\", "/")
    });
  }
  return endpoints;
}

const javaFiles = (await files(javaRoot)).filter((file) => file.endsWith("Resource.java"));
const endpoints = (await Promise.all(javaFiles.map(async (file) =>
  discover(await fs.readFile(file, "utf8"), file)))).flat()
  .sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method));

const paths = {};
for (const endpoint of endpoints) {
  const parameters = [...endpoint.path.matchAll(/\{([^}]+)\}/g)].map((match) => ({
    name: match[1], in: "path", required: true, schema: { type: "string" }
  }));
  paths[endpoint.path] ??= {};
  paths[endpoint.path][endpoint.method] = {
    operationId: endpoint.operationId,
    tags: [endpoint.source.split("/").at(-3) ?? "StayHub"],
    parameters,
    responses: {
      "200": { description: "Operación procesada correctamente" },
      "400": { description: "Solicitud inválida", content: { "application/problem+json": { schema: { $ref: "#/components/schemas/Problem" } } } },
      "401": { description: "Autenticación requerida" },
      "403": { description: "Operación no autorizada" },
      "500": { description: "Error interno", content: { "application/problem+json": { schema: { $ref: "#/components/schemas/Problem" } } } }
    },
    "x-stayhub-source": endpoint.source
  };
}

const document = {
  openapi: "3.1.0",
  info: { title: "StayHub REST API", version: "1.0.0", description: "Contrato generado desde los recursos JAX-RS versionados." },
  servers: [{ url: "https://localhost:8443/StayHub" }],
  paths,
  components: { schemas: { Problem: { type: "object", required: ["status", "title"], properties: {
    type: { type: "string" }, title: { type: "string" }, status: { type: "integer" }, detail: { type: "string" }, instance: { type: "string" }
  } } } }
};

await fs.mkdir(path.dirname(output), { recursive: true });
await fs.writeFile(output, `${JSON.stringify(document, null, 2)}\n`);
console.log(`OpenAPI generado: ${endpoints.length} operaciones en ${Object.keys(paths).length} rutas.`);
