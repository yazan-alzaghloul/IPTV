import { defineConfig, devices } from "@playwright/test";
import { mkdtempSync, mkdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
// Isolate Firefox app data from the user's macOS-protected personal profile.
// https://github.com/microsoft/playwright/issues/42768
const firefoxData = mkdtempSync(join(tmpdir(), "rayan-firefox-data-"));
mkdirSync(join(firefoxData, "data"));
mkdirSync(join(firefoxData, "cache"));
process.on("exit", () => rmSync(firefoxData, { recursive: true, force: true }));
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  expect: { timeout: 15000 },
  reporter: [
    ["list"],
    ["html", { open: "never" }],
    ["json", { outputFile: "test-results/results.json" }],
  ],
  use: {
    baseURL: "http://127.0.0.1:3100",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    {
      name: "firefox",
      use: {
        ...devices["Desktop Firefox"],
        launchOptions: {
          env: {
            ...process.env,
            MOZ_APP_DATA: join(firefoxData, "data"),
            MOZ_LOCAL_APP_DATA: join(firefoxData, "cache"),
          },
        },
      },
    },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
  webServer: [
    {
      command:
        "python3 ../../scripts/fixture-provider.py --port 18765 --catalog-size 105",
      url: "http://127.0.0.1:18765/poster.jpg",
      reuseExistingServer: false,
    },
    {
      command: "pnpm dev --port 3100",
      url: "http://127.0.0.1:3100/login",
      reuseExistingServer: false,
      timeout: 120000,
      env: {
        PROVIDER_ORIGINS: "http://127.0.0.1:18765,http://localhost:18765",
        MEDIA_ORIGINS: "",
        ALLOW_PRIVATE_PROVIDERS: "true",
        APP_ORIGIN: "http://127.0.0.1:3100",
        SESSION_SECRET: "playwright-test-secret-only-not-for-production",
      },
    },
  ],
});
