import { test, expect, type Page } from '@playwright/test';

const user = { id: '11111111-1111-4111-8111-111111111111', aud: 'authenticated', role: 'authenticated', email: 'fixture@example.invalid', app_metadata: {}, user_metadata: {}, created_at: new Date().toISOString() };
const token = `${Buffer.from('{"alg":"HS256","typ":"JWT"}').toString('base64url')}.${Buffer.from(JSON.stringify({ sub: user.id, aud: 'authenticated', role: 'authenticated', exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.test-signature`;
const session = { access_token: token, refresh_token: 'test-refresh-token', token_type: 'bearer', expires_in: 3600, user };
const recoveryHash = `#access_token=${token}&refresh_token=test-refresh-token&expires_in=3600&token_type=bearer&type=recovery`;

async function mockAuth(page: Page, recoverStatus = 200) {
  const requests: { path: string; body: Record<string, string> }[] = [];
  await page.route('**/auth/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const body = request.postDataJSON() ?? {};
    requests.push({ path, body });
    const status = path.endsWith('/recover') ? recoverStatus : 200;
    const response = path.endsWith('/recover') ? (status === 200 ? {} : { msg: 'Private account-dependent error' })
      : path.endsWith('/user') ? user
      : path.endsWith('/logout') ? {}
      : { ...session, session };
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) });
  });
  return requests;
}

async function openForgot(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in', exact: true }).first().click();
  await page.getByTestId('button-forgot-password').click();
}

for (const status of [200, 400, 429, 500]) {
  test(`reset requests keep account details private for response ${status}`, async ({ page }) => {
    const requests = await mockAuth(page, status);
    await openForgot(page);
    await page.getByTestId('input-reset-email').fill('fixture@example.invalid');
    await page.getByTestId('button-request-reset').click();
    await expect(page.getByTestId('status-password-recovery')).toContainText('If an account exists');
    await expect(page.getByText('Private account-dependent error')).toHaveCount(0);
    const request = requests.find(r => r.path.endsWith('/recover'));
    expect(request?.body.email).toBe('fixture@example.invalid');
    // Supabase sends the recovery redirect as a query parameter, checked below.
    await page.getByTestId('button-recovery-signin').click();
    await expect(page.getByRole('heading', { name: 'Good to see you again.' })).toBeVisible();
  });
}

test('reset redirect uses the current artifact path', async ({ page }) => {
  await mockAuth(page);
  await page.route('**/src/lib/supabase.ts', async route => {
    const response = await route.fetch();
    const source = (await response.text()).replace('new URL(import.meta.env.BASE_URL,', 'new URL("/nested/trolley/",');
    await route.fulfill({ response, body: source });
  });
  await openForgot(page);
  await page.getByTestId('input-reset-email').fill('fixture@example.invalid');
  const request = page.waitForRequest(r => r.url().includes('/auth/v1/recover'));
  await page.getByTestId('button-request-reset').click();
  const redirect = new URL((await request).url()).searchParams.get('redirect_to');
  const target = new URL(redirect!);
  expect(target.pathname).toBe('/nested/trolley/');
  expect(target.searchParams.get('auth')).toBe('recovery');
  expect(target.origin).toBe(new URL(page.url()).origin);
});

test('a recovery link supports reload, password validation, saving and signing in', async ({ page }) => {
  const requests = await mockAuth(page);
  await page.goto(`/?auth=recovery${recoveryHash}`);
  await expect(page.getByTestId('input-new-password')).toBeVisible();
  await expect.poll(() => new URL(page.url()).hash).toBe('');
  await page.reload();
  await expect(page.getByTestId('input-new-password')).toBeVisible();
  await page.getByTestId('input-new-password').fill('new-password-123!');
  await page.getByTestId('input-confirm-password').fill('not-the-same-123!');
  await page.getByTestId('button-save-password').click();
  await expect(page.getByTestId('status-password-recovery')).toContainText('do not match');
  expect(requests.filter(r => r.body.password)).toHaveLength(0);
  await page.getByTestId('input-confirm-password').fill('new-password-123!');
  await page.getByTestId('button-save-password').click();
  await expect(page.getByRole('heading', { name: 'Password updated' })).toBeVisible();
  expect(requests.some(r => r.path.endsWith('/user') && r.body.password === 'new-password-123!')).toBe(true);
  expect(requests.some(r => r.path.endsWith('/logout'))).toBe(true);
  await page.getByTestId('button-recovery-signin').click();
  await page.getByRole('textbox', { name: 'Email address' }).fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill('new-password-123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).last().click();
  await expect(page.getByText('You are signed in.')).toBeVisible();
});

test('expired links show recovery instructions and remove sensitive URL data', async ({ page }) => {
  await mockAuth(page);
  await page.goto('/?auth=recovery#error=access_denied&error_code=otp_expired&error_description=Expired');
  await expect(page.getByTestId('status-recovery-invalid')).toBeVisible();
  await expect(page.getByTestId('input-new-password')).toHaveCount(0);
  await expect.poll(() => new URL(page.url()).hash).toBe('');
  await page.getByTestId('button-recovery-signin').click();
  await page.getByTestId('button-forgot-password').click();
  await expect(page.getByTestId('input-reset-email')).toBeVisible();
});

test('a recovery marker alone cannot authorize a password change', async ({ page }) => {
  await mockAuth(page);
  await page.goto('/?auth=recovery');
  await expect(page.getByTestId('status-recovery-invalid')).toBeVisible();
});

test('signup remains available', async ({ page }) => {
  const requests = await mockAuth(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Get started' }).click();
  await page.getByRole('textbox', { name: 'Full name' }).fill('Synthetic fixture');
  await page.getByRole('textbox', { name: 'Email address' }).fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill('signup-password-123!');
  await page.getByLabel('Date of birth').fill('1995-01-01');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page.getByText('You are signed in.')).toBeVisible();
  expect(requests.some(r => r.path.endsWith('/signup'))).toBe(true);
});
