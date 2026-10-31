import { promises as fs } from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const document = JSON.parse(await fs.readFile(path.join(root, "contracts", "openapi", "stayhub-openapi.json"), "utf8"));
if (document.openapi !== "3.1.0" || !document.info?.title || !document.paths) {
  throw new Error("El documento OpenAPI no contiene la estructura mínima requerida.");
}

const methods = new Set(["get", "post", "put", "patch", "delete"]);
const operations = Object.entries(document.paths).flatMap(([route, item]) =>
  Object.entries(item).filter(([method]) => methods.has(method))
    .map(([method, operation]) => ({ route, method, operation })));
const schemas = document.components?.schemas ?? {};
if (Object.keys(schemas).length < 10) throw new Error("El contrato no contiene esquemas de dominio suficientes.");

function refs(value, result = []) {
  if (!value || typeof value !== "object") return result;
  if (typeof value.$ref === "string") result.push(value.$ref);
  for (const nested of Object.values(value)) refs(nested, result);
  return result;
}

const ids = new Set();
for (const { route, method, operation } of operations) {
  const label = `${method.toUpperCase()} ${route}`;
  if (!operation.operationId) throw new Error(`${label} no tiene operationId.`);
  if (ids.has(operation.operationId)) throw new Error(`operationId duplicado: ${operation.operationId}`);
  ids.add(operation.operationId);
  if (!Object.keys(operation.responses ?? {}).some((code) => /^2\d\d$/.test(code))) {
    throw new Error(`${label} no documenta una respuesta exitosa.`);
  }
  for (const code of ["400", "401", "403", "404", "409", "500", "502", "503"]) {
    if (!operation.responses?.[code]) throw new Error(`${label} no documenta el error ${code}.`);
  }
  for (const name of [...route.matchAll(/\{([^}]+)\}/g)].map((match) => match[1])) {
    if (!operation.parameters?.some((parameter) => parameter.in === "path" && parameter.name === name)) {
      throw new Error(`${label} no documenta el parámetro ${name}.`);
    }
  }
  for (const ref of refs(operation)) {
    const prefix = "#/components/schemas/";
    if (ref.startsWith(prefix) && !schemas[ref.slice(prefix.length)]) {
      throw new Error(`${label} referencia un esquema inexistente: ${ref}.`);
    }
  }
}

const postmanDir = path.join(root, "postman");
const collections = (await fs.readdir(postmanDir)).filter((name) => name.endsWith(".postman_collection.json"));
let postmanRequests = 0;
let postmanRequestsWithTests = 0;
function collectionRequests(items, result = []) {
  for (const item of items ?? []) {
    if (item.request) result.push(item);
    collectionRequests(item.item, result);
  }
  return result;
}
for (const name of collections) {
  const collection = JSON.parse(await fs.readFile(path.join(postmanDir, name), "utf8"));
  if (!collection.info?.name || !Array.isArray(collection.item)) throw new Error(`Colección inválida: ${name}`);
  const requestItems = collectionRequests(collection.item);
  postmanRequests += requestItems.length;
  for (const item of requestItems) {
    const hasTests = (item.event ?? []).some((event) =>
      event.listen === "test" && Array.isArray(event.script?.exec) && event.script.exec.length > 0);
    if (!hasTests) throw new Error(`${name}: ${item.name} no tiene aserciones.`);
    postmanRequestsWithTests += 1;
  }
  if (!name.includes("IntegracionPmsSOAP")
      && !collection.item.some((item) => item.name === "CI - Contrato mínimo")) {
    throw new Error(`${name} no contiene el escenario reproducible CI - Contrato mínimo.`);
  }
}

console.log(`Contrato válido: ${operations.length} operaciones REST, ${Object.keys(schemas).length} esquemas, `
  + `${collections.length} colecciones y ${postmanRequestsWithTests}/${postmanRequests} requests con aserciones.`);
