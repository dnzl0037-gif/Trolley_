import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  outputDir: '../../generated-artifacts/trolley-tests',
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.REPLIT_DEV_DOMAIN ? `https://${process.env.REPLIT_DEV_DOMAIN}` : 'http://localhost:80',
    headless: true,
    launchOptions: { executablePath: '/repl/tools/bin/chromium', args: ['--no-sandbox'] },
    trace: 'off',
    screenshot: 'off',
  },
});
