const { defineConfig, devices } = require('@playwright/test');

const hostResolverRules = process.env.PPE_P20_HOST_RESOLVER_RULES;

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
    launchOptions: {
      args: hostResolverRules ? [`--host-resolver-rules=${hostResolverRules}`] : []
    },
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
