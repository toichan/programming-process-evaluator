const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './phase20',
  fullyParallel: false,
  workers: 1,
  timeout: 50_000,
  expect: {
    timeout: 5_000
  },
  reporter: 'list',
  use: {
    baseURL: process.env.PPE_P20_BASE_URL || 'http://student.localhost:18089',
    headless: true,
    viewport: { width: 1280, height: 900 },
    trace: 'retain-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
