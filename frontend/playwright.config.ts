import { defineConfig } from '@playwright/test'

const viewportProjects = [
  { name: 'chromium-1440', width: 1440, height: 1000 },
  { name: 'chromium-1024', width: 1024, height: 900 },
  { name: 'chromium-768', width: 768, height: 900 },
  { name: 'chromium-390', width: 390, height: 844 },
  { name: 'chromium-320', width: 320, height: 700 },
]

export default defineConfig({
  testDir: './e2e',
  snapshotPathTemplate: '{testDir}/{testFilePath}-snapshots/{arg}-{projectName}{ext}',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    browserName: 'chromium',
    colorScheme: 'light',
    contextOptions: { reducedMotion: 'reduce' },
    deviceScaleFactor: 1,
    locale: 'ru-RU',
    screenshot: 'only-on-failure',
    timezoneId: 'Europe/Moscow',
    trace: 'retain-on-failure',
  },
  expect: {
    toHaveScreenshot: {
      animations: 'disabled',
      maxDiffPixelRatio: 0.01,
    },
  },
  projects: viewportProjects.map(({ name, width, height }) => ({
    name,
    use: { viewport: { width, height } },
  })),
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
  },
})
