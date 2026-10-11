import { defineConfig, devices } from '@playwright/test'

const PORT = 4173

// Functional tests run against a fresh production bundle served by
// `vite preview`, with every backend call mocked in e2e/fixtures.ts. No gateway
// is needed.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${PORT}/trino-gateway/`,
    locale: 'en-US',
    timezoneId: 'UTC',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: `pnpm exec vite build && pnpm exec vite preview --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}/trino-gateway/`,
    reuseExistingServer: !process.env.CI,
  },
})
