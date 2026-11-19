import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';

test('a real owner creates, edits, reloads and submits a private provider draft', async ({ page, browser, baseURL }) => {
  test.skip(process.env.TROLLEY_LIVE_PROVIDER !== '1', 'Requires the authorized dedicated test project and editor migration.');
  const id = randomUUID();
  const fixture = (action: string) => {
    // This helper hard-refuses any target other than the authorized test project.
    const result = spawnSync('python3', ['scripts/recovery-fixture.py', action], {
      input: JSON.stringify({ id }), encoding: 'utf8',
    });
    expect(result.status === 0, `Isolated provider fixture ${action} failed`).toBe(true);
    return JSON.parse(result.stdout);
  };
  try {
    const account = fixture('create');
    const verify = new URL(`${process.env.VITE_SUPABASE_URL}/auth/v1/verify`);
    verify.searchParams.set('token', account.token_hash);
    verify.searchParams.set('type', 'recovery');
    const result = await fetch(verify, {
      redirect: 'manual', headers: { apikey: process.env.VITE_SUPABASE_ANON_KEY! },
    });
    const params = new URLSearchParams(new URL(result.headers.get('location')!).hash.slice(1));
    expect(params.has('access_token'), 'Fixture login must be accepted').toBe(true);
    // Log in the synthetic account without entering the separate password-reset UI.
    params.set('type', 'signup');
    await page.goto(`${baseURL}/#${params}`);
    await page.getByRole('button', { name: 'My business' }).click();
    await page.getByLabel('Business name', { exact: true }).fill('Synthetic provider draft');
    await page.getByLabel('Short business description').fill('Original synthetic description');
    await page.getByRole('button', { name: 'Save provider draft', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toBeVisible();
    await expect(page.getByLabel('Business name', { exact: true })).toHaveValue('Synthetic provider draft');
    await page.getByLabel('Business name', { exact: true }).fill('Edited synthetic provider');
    await page.getByLabel('Short business description').fill('Changed and stored description');
    await page.getByLabel('Main service category').selectOption('electrical-lighting-installation');
    await page.getByLabel('City', { exact: true }).fill('Bulawayo');
    await page.getByLabel('Country code').fill('ZA');
    await page.getByRole('button', { name: 'Save changes', exact: true }).click();
    await expect(page.getByText('Changes saved.', { exact: false })).toBeVisible();
    await expect(page.getByLabel('Business name', { exact: true })).toHaveValue('Edited synthetic provider');
    // A fresh browser context must load the records from Supabase, not React state.
    const context = await browser.newContext({ storageState: await page.context().storageState() });
    try {
      const returning = await context.newPage();
      await returning.goto(baseURL!);
      await returning.getByRole('button', { name: 'My business' }).click();
      await expect(returning.getByLabel('Short business description')).toHaveValue('Changed and stored description');
      await expect(returning.getByLabel('Main service category')).toHaveValue('electrical-lighting-installation');
      await expect(returning.getByLabel('City', { exact: true })).toHaveValue('Bulawayo');
      await expect(returning.getByLabel('Country code')).toHaveValue('ZA');
      await returning.getByRole('button', { name: 'Submit for review', exact: true }).click();
      await expect(returning.getByText('Listing status:')).toContainText('pending review');
      await expect(returning.getByText('Verification:', { exact: false })).toContainText('not submitted');
      await returning.reload();
      await returning.getByRole('button', { name: 'My business' }).click();
      await expect(returning.getByText('Listing status:')).toContainText('pending review');
    } finally {
      await context.close();
    }
  } finally {
    fixture('delete');
  }
});
