import { promises as fs } from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const openapiPath = path.join(root, "contracts", "openapi", "stayhub-openapi.json");
const document = JSON.parse(await fs.readFile(openapiPath, "utf8"));

if (document.openapi !== "3.1.0" || !document.info?.title || !document.paths) {
  throw new Error("El documento OpenAPI no contiene la estructura mínima requerida.");
}

const operations = Object.entries(document.paths).flatMap(([route, item]) =>
  Object.entries(item).filter(([method]) => ["get", "post", "put", "patch", "delete"].includes(method))
    .map(([method, operation]) => ({ route, method, operation })));

const ids = new Set();
for (const { route, method, operation } of operations) {
  if (!operation.operationId) throw new Error(`${method.toUpperCase()} ${route} no tiene operationId.`);
  if (ids.has(operation.operationId)) throw new Error(`operationId duplicado: ${operation.operationId}`);
  ids.add(operation.operationId);
  if (!operation.responses?.["400"] || !operation.responses?.["500"]) {
    throw new Error(`${method.toUpperCase()} ${route} no documenta respuestas de error.`);
  }
  for (const name of [...route.matchAll(/\{([^}]+)\}/g)].map((match) => match[1])) {
    if (!operation.parameters?.some((parameter) => parameter.in === "path" && parameter.name === name)) {
      throw new Error(`${method.toUpperCase()} ${route} no documenta el parámetro ${name}.`);
    }
  }
}

const postmanDir = path.join(root, "postman");
const collections = (await fs.readdir(postmanDir)).filter((name) => name.endsWith(".postman_collection.json"));
for (const name of collections) {
  const collection = JSON.parse(await fs.readFile(path.join(postmanDir, name), "utf8"));
  if (!collection.info?.name || !Array.isArray(collection.item)) throw new Error(`Colección inválida: ${name}`);
}

console.log(`Contrato válido: ${operations.length} operaciones REST y ${collections.length} colecciones Postman.`);
