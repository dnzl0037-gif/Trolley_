import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';

// Opt-in only. The helper refuses any project except the owner's dedicated test
// target. No existing account, password, or settings are changed.
for (const checkEmailRedirect of [true, false]) {
test(checkEmailRedirect ? 'Supabase accepts the email recovery redirect to Trolley' : 'a real recovery session can save a password and sign in', async ({ page, baseURL }) => {
  test.skip(process.env.TROLLEY_LIVE_RECOVERY !== '1', 'Requires the authorized test database.');
  const id = randomUUID();
  const fixture = (action: string) => {
    const result = spawnSync('python3', ['scripts/recovery-fixture.py', action], {
      input: JSON.stringify({ id }), encoding: 'utf8',
    });
    expect(result.status === 0, `Isolated recovery fixture ${action} failed`).toBe(true);
    return JSON.parse(result.stdout);
  };
  const api = process.env.VITE_SUPABASE_URL!;
  const key = process.env.VITE_SUPABASE_ANON_KEY!;
  const password = `Recovery-${randomUUID()}-aA1!`;
  try {
    const account = fixture('create');
    const redirect = new URL('/?auth=recovery', baseURL).href;
    // This is the same GoTrue verify URL used by Supabase recovery emails. The
    // synthetic token avoids sending mail or reading/changing an existing user.
    const verify = new URL(`${api}/auth/v1/verify`);
    verify.searchParams.set('token', account.token_hash);
    verify.searchParams.set('type', 'recovery');
    verify.searchParams.set('redirect_to', redirect);
    const result = await fetch(verify, { redirect: 'manual', headers: { apikey: key } });
    const location = result.headers.get('location');
    expect(Boolean(location), 'Supabase must return a recovery redirect').toBe(true);
    const target = new URL(location!);
    const linkError = new URLSearchParams(target.hash.slice(1));
    if (linkError.has('error')) {
      console.info('Supabase verification result:', linkError.get('error_code'), linkError.get('error_description'));
    }
    if (checkEmailRedirect) {
      expect(target.origin === new URL(redirect).origin && target.pathname === new URL(redirect).pathname && target.searchParams.get('auth') === 'recovery',
        'Supabase rejected the Trolley recovery redirect; allowlist the exact artifact recovery URL in Supabase Auth').toBe(true);
      return;
    }
    expect(new URLSearchParams(target.hash.slice(1)).has('access_token'), 'The fixture recovery token must be accepted').toBe(true);
    // Separately verify the real recovery session against the application,
    // even when the external project's redirect allowlist is not configured.
    // This does NOT substitute for the strict email redirect check above.
    await page.goto(redirect + target.hash);
    await expect(page.getByTestId('input-new-password')).toBeVisible();
    await page.getByTestId('input-new-password').fill(password);
    await page.getByTestId('input-confirm-password').fill(password);
    await page.getByTestId('button-save-password').click();
    await expect(page.getByRole('heading', { name: 'Password updated' })).toBeVisible();
    await page.getByTestId('button-recovery-signin').click();
    await page.getByRole('textbox', { name: 'Email address' }).fill(account.email);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Sign in', exact: true }).last().click();
    await expect(page.getByText('You are signed in.')).toBeVisible();
    // Reusing the consumed email link must no longer create a reset session.
    const reused = await fetch(verify, { redirect: 'manual', headers: { apikey: key } });
    expect(new URLSearchParams(new URL(reused.headers.get('location')!).hash.slice(1)).has('access_token'),
      'Recovery links must be single-use').toBe(false);
  } finally {
    fixture('delete');
  }
});
}
