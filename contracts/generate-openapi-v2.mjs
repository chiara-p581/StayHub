import { promises as fs } from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const javaRoot = path.join(root, "proyecto", "src", "main", "java");
const output = path.join(root, "contracts", "openapi", "stayhub-openapi.json");
const HTTP_METHODS = new Set(["GET", "POST", "PUT", "PATCH", "DELETE"]);

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

function splitTopLevel(value) {
  const result = [];
  let start = 0;
  let angle = 0;
  let round = 0;
  for (let i = 0; i < value.length; i += 1) {
    if (value[i] === "<") angle += 1;
    if (value[i] === ">") angle -= 1;
    if (value[i] === "(") round += 1;
    if (value[i] === ")") round -= 1;
    if (value[i] === "," && angle === 0 && round === 0) {
      result.push(value.slice(start, i).trim());
      start = i + 1;
    }
  }
  const last = value.slice(start).trim();
  if (last) result.push(last);
  return result;
}

function annotationValue(source, name) {
  return source.match(new RegExp(`@${name}\\("([^"]+)"\\)`))?.[1];
}

function javaTypeSchema(rawType) {
  const type = rawType.replaceAll(/\s+/g, "").replace(/^final/, "");
  const collection = type.match(/^(?:List|Set|Collection)<(.+)>$/);
  if (collection) return { type: "array", items: javaTypeSchema(collection[1]) };
  if (/^Map<.*>$/.test(type)) return { type: "object", additionalProperties: true };
  if (/^(?:String|Character|char)$/.test(type)) return { type: "string" };
  if (type === "LocalDate") return { type: "string", format: "date" };
  if (/^(?:LocalDateTime|OffsetDateTime|Instant)$/.test(type)) return { type: "string", format: "date-time" };
  if (/^(?:Long|long)$/.test(type)) return { type: "integer", format: "int64" };
  if (/^(?:Integer|int|Short|short|Byte|byte)$/.test(type)) return { type: "integer", format: "int32" };
  if (/^(?:BigDecimal|Double|double|Float|float)$/.test(type)) return { type: "number" };
  if (/^(?:Boolean|boolean)$/.test(type)) return { type: "boolean" };
  if (/^(?:void|Void)$/.test(type)) return null;
  if (type === "Response" || type.startsWith("Response<")) return { type: "object" };
  return { $ref: `#/components/schemas/${type.replaceAll(/[^A-Za-z0-9_.]/g, "")}` };
}

function parseParameters(source) {
  return splitTopLevel(source).map((raw) => {
    const location = ["PathParam", "QueryParam", "HeaderParam", "CookieParam", "FormParam"]
      .map((name) => ({ name, value: annotationValue(raw, name) }))
      .find((candidate) => candidate.value);
    const context = /@Context\b/.test(raw);
    const cleaned = raw.replaceAll(/@\w+(?:\([^)]*\))?\s*/g, "").replaceAll(/\s+/g, " ").trim();
    const match = cleaned.match(/(.+)\s+(\w+)$/);
    return {
      type: match?.[1]?.trim() ?? "Object",
      location: location?.name,
      name: location?.value,
      context
    };
  });
}

function successStatus(verb, body) {
  if (/Response\.Status\.CREATED|Response\.created\s*\(/.test(body)) return "201";
  if (/Response\.Status\.ACCEPTED|Response\.accepted\s*\(/.test(body)) return "202";
  if (/Response\.noContent\s*\(/.test(body)) return "204";
  if (verb === "DELETE" && !/return\s+[^;]+;/.test(body)) return "204";
  return "200";
}

function findBody(source, openingBrace) {
  let depth = 0;
  for (let i = openingBrace; i < source.length; i += 1) {
    if (source[i] === "{") depth += 1;
    if (source[i] === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(openingBrace + 1, i);
    }
  }
  return "";
}

function discover(source, file) {
  const classIndex = source.search(/public\s+class\s+\w+/);
  if (classIndex < 0) return [];
  const classPrefix = source.slice(0, classIndex);
  const classPaths = [...classPrefix.matchAll(/@Path\("([^"]+)"\)/g)];
  if (!classPaths.length) return [];
  const base = classPaths.at(-1)[1];
  const endpoints = [];
  const declaration = /((?:\s*@[A-Za-z][^\n]*\n)+)\s*public\s+([\w<>?,.\[\]\s]+?)\s+(\w+)\s*\(([\s\S]*?)\)\s*(?:throws\s+[^\{]+)?\{/g;
  const methodsSource = source.slice(classIndex);
  for (const match of methodsSource.matchAll(declaration)) {
    const annotations = match[1];
    const verbMatch = annotations.match(/@(GET|POST|PUT|PATCH|DELETE)\b/);
    if (!verbMatch || !HTTP_METHODS.has(verbMatch[1])) continue;
    const verb = verbMatch[1];
    const subPath = annotationValue(annotations, "Path") ?? "";
    const parameters = parseParameters(match[4]);
    const brace = classIndex + match.index + match[0].lastIndexOf("{");
    endpoints.push({
      method: verb.toLowerCase(),
      verb,
      path: normalisePath(`${base}/${subPath}`),
      operationId: `${path.basename(file, ".java").replace(/Resource$/, "")}_${match[3]}`,
      source: path.relative(root, file).replaceAll("\\", "/"),
      returnType: match[2].trim(),
      parameters,
      body: findBody(source, brace)
    });
  }
  return endpoints;
}

function schemaName(file, simpleName, duplicates) {
  if (!duplicates.has(simpleName)) return simpleName;
  const packageName = file.match(/com[\\/]+stayhub[\\/]+([^\\/]+)/)?.[1] ?? "stayhub";
  return `${packageName}_${simpleName}`;
}

async function discoverSchemas(javaFiles) {
  const candidates = [];
  for (const file of javaFiles) {
    const source = await fs.readFile(file, "utf8");
    for (const match of source.matchAll(/public\s+record\s+(\w+)\s*\(([\s\S]*?)\)\s*\{/g)) {
      candidates.push({ file, name: match[1], components: splitTopLevel(match[2]) });
    }
    for (const match of source.matchAll(/public\s+enum\s+(\w+)\s*\{([^}]*)\}/g)) {
      const values = match[2].split(",").map((value) => value.trim().match(/^([A-Z][A-Z0-9_]*)/)?.[1]).filter(Boolean);
      candidates.push({ file, name: match[1], enumValues: values });
    }
  }
  const counts = new Map();
  for (const candidate of candidates) counts.set(candidate.name, (counts.get(candidate.name) ?? 0) + 1);
  const duplicates = new Set([...counts].filter(([, count]) => count > 1).map(([name]) => name));
  const aliases = new Map();
  const schemas = {};
  for (const candidate of candidates) {
    const name = schemaName(candidate.file, candidate.name, duplicates);
    if (!duplicates.has(candidate.name)) aliases.set(candidate.name, name);
    if (candidate.enumValues) {
      schemas[name] = { type: "string", enum: candidate.enumValues };
      continue;
    }
    const properties = {};
    const required = [];
    for (const component of candidate.components) {
      const clean = component.replaceAll(/@\w+(?:\([^)]*\))?\s*/g, "").replaceAll(/\s+/g, " ").trim();
      const part = clean.match(/(.+)\s+(\w+)$/);
      if (!part) continue;
      properties[part[2]] = javaTypeSchema(part[1]) ?? {};
      required.push(part[2]);
    }
    schemas[name] = { type: "object", properties, ...(required.length ? { required } : {}) };
  }
  return { schemas, aliases, duplicates };
}

function resolveSchema(schema, aliases, duplicates, source) {
  if (!schema?.$ref) {
    if (schema?.items) schema.items = resolveSchema(schema.items, aliases, duplicates, source);
    return schema;
  }
  const simpleName = schema.$ref.split("/").at(-1);
  if (aliases.has(simpleName)) return { $ref: `#/components/schemas/${aliases.get(simpleName)}` };
  if (duplicates.has(simpleName)) {
    const packageName = source.match(/com\/stayhub\/([^/]+)/)?.[1] ?? "stayhub";
    return { $ref: `#/components/schemas/${packageName}_${simpleName}` };
  }
  return schema;
}

const javaFiles = (await files(javaRoot)).filter((file) => file.endsWith(".java"));
const resourceFiles = javaFiles.filter((file) => file.endsWith("Resource.java"));
const endpoints = (await Promise.all(resourceFiles.map(async (file) =>
  discover(await fs.readFile(file, "utf8"), file)))).flat()
  .sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method));
const { schemas, aliases, duplicates } = await discoverSchemas(javaFiles);

schemas.Problem = {
  type: "object",
  required: ["status", "title"],
  properties: {
    type: { type: "string" }, title: { type: "string" }, status: { type: "integer" },
    detail: { type: "string" }, instance: { type: "string" }, codigo: { type: "string" },
    mensaje: { type: "string" }
  }
};

const paths = {};
for (const endpoint of endpoints) {
  const documentedParameters = endpoint.parameters.filter((parameter) => parameter.location && !parameter.context)
    .map((parameter) => ({
      name: parameter.name,
      in: parameter.location.replace("Param", "").toLowerCase(),
      required: parameter.location === "PathParam",
      schema: resolveSchema(javaTypeSchema(parameter.type), aliases, duplicates, endpoint.source)
    }));
  const bodyParameter = endpoint.parameters.find((parameter) =>
    !parameter.location && !parameter.context && !/^(?:HttpServletRequest|HttpSession|UriInfo|SecurityContext)$/.test(parameter.type));
  const success = successStatus(endpoint.verb, endpoint.body);
  const responseSchema = resolveSchema(javaTypeSchema(endpoint.returnType), aliases, duplicates, endpoint.source);
  const operation = {
    operationId: endpoint.operationId,
    tags: [endpoint.source.split("/").at(-3) ?? "StayHub"],
    parameters: documentedParameters,
    responses: {
      [success]: {
        description: success === "201" ? "Recurso creado" : success === "202" ? "Solicitud aceptada" : success === "204" ? "Sin contenido" : "Operación procesada correctamente",
        ...(success !== "204" && responseSchema ? { content: { "application/json": { schema: responseSchema } } } : {})
      }
    },
    "x-stayhub-source": endpoint.source
  };
  if (bodyParameter && ["post", "put", "patch"].includes(endpoint.method)) {
    operation.requestBody = {
      required: true,
      content: { "application/json": { schema: resolveSchema(javaTypeSchema(bodyParameter.type), aliases, duplicates, endpoint.source) } }
    };
  }
  paths[endpoint.path] ??= {};
  paths[endpoint.path][endpoint.method] = operation;
}

const problemContent = { "application/problem+json": { schema: { $ref: "#/components/schemas/Problem" } } };
const response = (description) => ({ description, content: problemContent });
const errorResponses = {
  "400": { $ref: "#/components/responses/BadRequest" },
  "401": { $ref: "#/components/responses/Unauthorized" },
  "403": { $ref: "#/components/responses/Forbidden" },
  "404": { $ref: "#/components/responses/NotFound" },
  "409": { $ref: "#/components/responses/Conflict" },
  "500": { $ref: "#/components/responses/InternalError" },
  "502": { $ref: "#/components/responses/BadGateway" },
  "503": { $ref: "#/components/responses/Unavailable" }
};
for (const pathItem of Object.values(paths)) {
  for (const operation of Object.values(pathItem)) Object.assign(operation.responses, errorResponses);
}

const document = {
  openapi: "3.1.0",
  info: { title: "StayHub REST API", version: "1.0.0", description: "Contrato generado desde recursos JAX-RS y DTOs versionados." },
  servers: [{ url: "https://localhost:8443/StayHub" }],
  paths,
  components: {
    schemas,
    responses: {
      BadRequest: response("Solicitud inválida"),
      Unauthorized: response("Autenticación requerida"),
      Forbidden: response("Operación no autorizada"),
      NotFound: response("Recurso no encontrado"),
      Conflict: response("Conflicto de negocio o concurrencia"),
      InternalError: response("Error interno"),
      BadGateway: response("Error de integración externa"),
      Unavailable: response("Dependencia no disponible")
    }
  }
};

await fs.mkdir(path.dirname(output), { recursive: true });
await fs.writeFile(output, `${JSON.stringify(document, null, 2)}\n`);
console.log(`OpenAPI generado: ${endpoints.length} operaciones, ${Object.keys(paths).length} rutas y ${Object.keys(schemas).length} esquemas.`);
