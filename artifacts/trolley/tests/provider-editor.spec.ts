import { test, expect, type Page } from '@playwright/test';

const user = { id: '11111111-1111-4111-8111-111111111111', aud: 'authenticated', role: 'authenticated', email: 'editor@example.invalid', app_metadata: {}, user_metadata: {}, created_at: new Date().toISOString() };
const token = `${Buffer.from('{"alg":"HS256","typ":"JWT"}').toString('base64url')}.${Buffer.from(JSON.stringify({ sub: user.id, exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.fixture`;
const session = { access_token: token, refresh_token: 'fixture-refresh', token_type: 'bearer', expires_in: 3600, user };

async function setup(page: Page) {
  const state = {
    profile: {
      id: '22222222-2222-4222-8222-222222222222', business_name: 'Saved business', description: 'Saved description',
      verification_status: 'not_submitted', is_complete: false,
      services: [{ id: 'service', status: 'draft', categories: { slug: 'plumbing-leak-detection' } }],
      provider_locations: [{ id: 'location', city: 'Harare', country_code: 'ZW' }],
    },
    mutationError: false, readError: false,
    requests: [] as Record<string, unknown>[],
  };
  await page.route('**/auth/v1/**', route => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(route.request().url().includes('/user') ? user : session),
  }));
  await page.route('**/rest/v1/provider_profiles?*', route => route.fulfill({
    status: state.readError ? 500 : 200, contentType: 'application/json',
    body: JSON.stringify(state.readError ? { message: 'Read-back unavailable' } : state.profile),
  }));
  await page.route('**/rest/v1/rpc/update_provider_draft', async route => {
    const body = route.request().postDataJSON();
    state.requests.push(body);
    if (state.mutationError) {
      await route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ message: 'Save rejected by server' }) });
      return;
    }
    state.profile.business_name = body.p_business_name.trim();
    state.profile.description = body.p_description;
    state.profile.provider_locations[0] = { id: 'location', city: body.p_city, country_code: body.p_country_code };
    state.profile.services[0].categories.slug = body.p_category_slug;
    if (body.p_submit) state.profile.services[0].status = 'pending_review';
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(state.profile.id) });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in', exact: true }).first().click();
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill('fixture-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).last().click();
  await expect(page.getByText('You are signed in.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await page.getByRole('button', { name: 'My business' }).click();
  await expect(page.getByLabel('Business name', { exact: true })).toHaveValue('Saved business');
  return state;
}

test('owner loads, saves, reopens and submits the stored draft', async ({ page }) => {
  const state = await setup(page);
  await expect(page.getByLabel('Short business description')).toHaveValue('Saved description');
  await expect(page.getByLabel('City', { exact: true })).toHaveValue('Harare');
  await page.getByLabel('Business name', { exact: true }).fill('Changed business');
  await page.getByLabel('Short business description').fill('');
  await page.getByLabel('Main service category').selectOption('electrical-lighting-installation');
  await page.getByLabel('City', { exact: true }).fill('Bulawayo');
  await page.getByLabel('Country code').fill('ZA');
  await page.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(page.getByText('Changes saved.', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Business name', { exact: true })).toHaveValue('Changed business');
  expect(state.requests[0]).toEqual({
    p_provider_id: state.profile.id, p_business_name: 'Changed business', p_description: '',
    p_city: 'Bulawayo', p_country_code: 'ZA', p_category_slug: 'electrical-lighting-installation', p_submit: false,
  });
  await page.reload();
  await page.getByRole('button', { name: 'My business' }).click();
  await expect(page.getByLabel('City', { exact: true })).toHaveValue('Bulawayo');
  await expect(page.getByLabel('Main service category')).toHaveValue('electrical-lighting-installation');
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await expect(page.getByText('Listing status:')).toContainText('pending review');
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toBeDisabled();
  expect(state.requests[1].p_submit).toBe(true);
  expect(state.profile.verification_status).toBe('not_submitted');
});

test('save failures retain edits and read-back failures offer retry', async ({ page }) => {
  const state = await setup(page);
  state.mutationError = true;
  await page.getByLabel('Business name', { exact: true }).fill('Unsaved business');
  await page.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Save rejected by server');
  await expect(page.getByLabel('Business name', { exact: true })).toHaveValue('Unsaved business');
  state.mutationError = false;
  state.readError = true;
  await page.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Read-back unavailable');
  await expect(page.getByRole('button', { name: 'Save provider draft' })).toHaveCount(0);
  state.readError = false;
  await page.getByRole('button', { name: 'Retry', exact: true }).click();
  await expect(page.getByLabel('Business name', { exact: true })).toHaveValue('Unsaved business');
});

test('non-draft and ambiguous profiles are not editable', async ({ page }) => {
  const state = await setup(page);
  state.profile.services[0].status = 'published';
  await page.getByRole('button', { name: 'Reload saved details' }).click();
  await expect(page.getByRole('alert')).toContainText('cannot be edited here');
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toHaveCount(0);
  state.profile.services[0].status = 'draft';
  state.profile.provider_locations.push({ id: 'second-location', city: 'Mutare', country_code: 'ZW' });
  await page.getByRole('button', { name: 'Reload saved details' }).click();
  await expect(page.getByRole('alert')).toContainText('cannot be edited here');
});
