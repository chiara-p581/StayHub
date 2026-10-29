const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI
    ? [["line"], ["html", { outputFolder: "playwright-report", open: "never" }], ["junit", { outputFile: "test-results/e2e-junit.xml" }]]
    : [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    baseURL: `${process.env.STAYHUB_BASE_URL || "https://localhost:8443/StayHub"}/`.replace(/\/\/+$/, "/"),
    ignoreHTTPSErrors: true,
    extraHTTPHeaders: { Accept: "application/json" },
    trace: "retain-on-failure"
  }
});
