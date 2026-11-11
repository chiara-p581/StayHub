import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "https://localhost:8443/StayHub/api").replace(/\/$/, "");
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || "5m";

const endpoints = [
  ["hoteles", "GET /hoteles"],
  ["disponibilidad", "GET /inventario-tarifas/disponibilidad"],
  ["tarifas", "GET /inventario-tarifas/tarifas"],
];

export const options = {
  insecureSkipTLSVerify: true,
  discardResponseBodies: false,
  scenarios: {
    critical_endpoints: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "15s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<500"],
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{endpoint:hoteles}": ["p(95)<500"],
    "http_req_failed{endpoint:hoteles}": ["rate<0.01"],
    "http_req_duration{endpoint:disponibilidad}": ["p(95)<500"],
    "http_req_failed{endpoint:disponibilidad}": ["rate<0.01"],
    "http_req_duration{endpoint:tarifas}": ["p(95)<500"],
    "http_req_failed{endpoint:tarifas}": ["rate<0.01"],
  },
};

function isoDate(daysFromToday) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + daysFromToday);
  return date.toISOString().slice(0, 10);
}

function isJsonArray(response) {
  try {
    return Array.isArray(response.json());
  } catch (_) {
    return false;
  }
}

export function setup() {
  const response = http.get(`${BASE_URL}/hoteles?limite=20`, {
    tags: { endpoint: "preparacion" },
  });
  if (response.status !== 200 || !isJsonArray(response) || response.json().length === 0) {
    throw new Error("No hay hoteles demo. Ejecute start-local antes de la prueba de carga.");
  }
  return {
    hotelId: Number(__ENV.HOTEL_ID || response.json()[0].id),
    desde: isoDate(7),
    hasta: isoDate(9),
  };
}

export default function (data) {
  const hotels = http.get(`${BASE_URL}/hoteles?limite=20`, {
    tags: { endpoint: "hoteles" },
  });
  check(hotels, {
    "hoteles responde 200": (response) => response.status === 200,
    "hoteles devuelve una lista": isJsonArray,
  });

  const query = `hotelId=${data.hotelId}&desde=${data.desde}&hasta=${data.hasta}`;
  const availability = http.get(`${BASE_URL}/inventario-tarifas/disponibilidad?${query}`, {
    tags: { endpoint: "disponibilidad" },
  });
  check(availability, {
    "disponibilidad responde 200": (response) => response.status === 200,
    "disponibilidad devuelve una lista": isJsonArray,
  });

  const rates = http.get(`${BASE_URL}/inventario-tarifas/tarifas?${query}`, {
    tags: { endpoint: "tarifas" },
  });
  check(rates, {
    "tarifas responde 200": (response) => response.status === 200,
    "tarifas devuelve una lista": isJsonArray,
  });

  // Pausa de navegación realista: mantiene 50 usuarios concurrentes sin
  // convertir cada usuario en un generador artificial de solicitudes.
  sleep(2);
}

function value(data, metricName, field) {
  return data.metrics[metricName]?.values?.[field];
}

function milliseconds(number) {
  return Number.isFinite(number) ? `${number.toFixed(2)} ms` : "sin datos";
}

function percentage(number) {
  return Number.isFinite(number) ? `${(number * 100).toFixed(3)}%` : "sin datos";
}

function thresholdPassed(data, metricName) {
  const thresholds = Object.values(data.metrics[metricName]?.thresholds || {});
  return thresholds.length > 0 && thresholds.every((threshold) => threshold.ok);
}

function markdownSummary(data) {
  const lines = [
    "# Informe de prueba de carga — KAN-98",
    "",
    `Fecha UTC: ${new Date().toISOString()}`,
    `Carga: ${VUS} usuarios virtuales concurrentes durante ${DURATION}.`,
    `Objetivo: p95 < 500 ms y tasa de error HTTP < 1%.`,
    "",
    "## Endpoints seleccionados",
    "",
    "- `GET /hoteles`: entrada al catálogo y al embudo de reserva.",
    "- `GET /inventario-tarifas/disponibilidad`: valida cupo antes de reservar.",
    "- `GET /inventario-tarifas/tarifas`: obtiene el precio mostrado y usado por la reserva.",
    "",
    "## Resultados",
    "",
    "| Endpoint | p95 | Error HTTP | p95 < 500 ms | Error < 1% |",
    "|---|---:|---:|:---:|:---:|",
  ];

  for (const [tag, label] of endpoints) {
    const durationMetric = `http_req_duration{endpoint:${tag}}`;
    const errorMetric = `http_req_failed{endpoint:${tag}}`;
    lines.push(`| ${label} | ${milliseconds(value(data, durationMetric, "p(95)"))} | ${percentage(value(data, errorMetric, "rate"))} | ${thresholdPassed(data, durationMetric) ? "Sí" : "No"} | ${thresholdPassed(data, errorMetric) ? "Sí" : "No"} |`);
  }

  lines.push(
    "",
    "## Resultado global",
    "",
    `- p95: ${milliseconds(value(data, "http_req_duration", "p(95)"))}`,
    `- Tasa de error HTTP: ${percentage(value(data, "http_req_failed", "rate"))}`,
    `- Checks correctos: ${percentage(value(data, "checks", "rate"))}`,
    `- Veredicto: **${thresholdPassed(data, "http_req_duration") && thresholdPassed(data, "http_req_failed") && thresholdPassed(data, "checks") ? "APROBADO" : "NO APROBADO"}**`,
    "",
  );
  return lines.join("\n");
}

export function handleSummary(data) {
  const report = markdownSummary(data);
  return {
    stdout: `${report}\n`,
    "performance/reports/k6-summary.md": `${report}\n`,
    "performance/reports/k6-summary.json": JSON.stringify(data, null, 2),
  };
}
