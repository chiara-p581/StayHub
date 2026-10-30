import { readFile } from "node:fs/promises";

const file = process.argv[2];
if (!file) throw new Error("Uso: node verify-wsdl.mjs <archivo.wsdl>");
const wsdl = await readFile(file, "utf8");
const operations = ["consultarReserva", "listarLlegadas", "cancelarReserva", "consultarDisponibilidad", "consultarTarifas"];
for (const operation of operations) {
  const expression = new RegExp(`<[^>]*operation[^>]+name=["']${operation}["']`, "i");
  if (!expression.test(wsdl)) throw new Error(`El WSDL no publica la operación ${operation}.`);
}
if (!/definitions|description/i.test(wsdl) || !/binding/i.test(wsdl) || !/service/i.test(wsdl)) {
  throw new Error("El documento no contiene las secciones contractuales WSDL requeridas.");
}
console.log(`WSDL válido: ${operations.length} operaciones SOAP publicadas.`);
