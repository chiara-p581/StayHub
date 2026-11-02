const { defineConfig } = require("@playwright/test");

const PUERTO = Number(process.env.UI_PORT || 4173);

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  workers: process.env.CI ? 2 : 4,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [["line"], ["html", { outputFolder: "reports/playwright", open: "never" }], ["junit", { outputFile: "reports/ui-junit.xml" }]]
    : [["list"], ["html", { outputFolder: "reports/playwright", open: "never" }]],
  use: {
    baseURL: `http://127.0.0.1:${PUERTO}/`,
    // Se respeta la preferencia de movimiento reducido: sin animaciones de entrada que
    // falsearían la medición de contraste mientras los elementos aún están transparentes.
    reducedMotion: "reduce",
    trace: "retain-on-failure"
  },
  webServer: {
    command: "node servidor-mock.mjs",
    env: { PORT: String(PUERTO) },
    url: `http://127.0.0.1:${PUERTO}/login.html`,
    reuseExistingServer: !process.env.CI,
    timeout: 30_000
  }
});
