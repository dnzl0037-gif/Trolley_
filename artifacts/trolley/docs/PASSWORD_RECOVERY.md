# Password recovery

Trolley uses the existing Supabase browser client and public anon/publishable
key. Passwords and recovery tokens go directly to Supabase Auth. No admin or
service-role keys belong in this app.

## Required Supabase setting

In the connected Supabase project's **Authentication → URL Configuration**,
add each trusted app's recovery URL to **Redirect URLs**:

```text
https://YOUR-APP-ORIGIN/ARTIFACT-BASE/?auth=recovery
```

For an artifact mounted at `/`, the URL is
`https://YOUR-APP-ORIGIN/?auth=recovery`. For `/trolley/`, it is
`https://YOUR-APP-ORIGIN/trolley/?auth=recovery`. Use the real browser origin,
not localhost, and allow the current development preview separately from
the published app. Avoid broad wildcards for published URLs.

The app builds this URL from the current browser origin and Vite's artifact
base path. It does not assume the artifact is at the domain root.
Supabase falls back to its configured Site URL when a redirect is not allowed;
application code cannot override that security setting.

Use the Supabase recovery email template's standard `{{ .ConfirmationURL }}`
link. A customized template must preserve the recovery type and the requested
redirect. Configure working email delivery in Supabase before launch.

## Behavior

- Sign in → Forgot password requests an email with a neutral confirmation for
  every server response, including mail-provider failures and rate limits.
  This deliberately avoids revealing whether an email belongs to an account.
- A valid recovery link opens the new-password form automatically. Reset
  credentials and provider errors are removed from the URL after processing.
- Only a Supabase recovery event, or a continued recovery session in the same
  tab after reload, enables the form. A query marker alone is not sufficient.
- Passwords must match and have at least eight characters. Supabase enforces
  any stronger project-specific password policy.
- Successful saving signs out locally and offers a return to sign in.
  Leaving a valid recovery flow also signs out locally.
- Expired or reused links show instructions to request a fresh link.

## Checks

Start the managed Trolley workflow, then:

```sh
pnpm --filter @workspace/trolley run test:recovery
pnpm --filter @workspace/trolley run typecheck
pnpm --filter @workspace/trolley run lint
```

The browser suite uses mocked Auth responses and a system Chromium executable.
It covers privacy-preserving reset responses, artifact-aware redirect
construction, recovery reloads, mismatched passwords, saving, login, expired
links, invalid markers and signup. Reports stay in ignored
`generated-artifacts/trolley-tests/`.

Opt-in live checks require the existing secrets for the authorized dedicated
Supabase test project:

```sh
TROLLEY_LIVE_RECOVERY=1 pnpm --filter @workspace/trolley exec playwright test password-recovery-live.spec.ts
```

These checks create a uniquely identified synthetic account and recovery token,
call the same verification endpoint used by email links, test the strict
redirect allowlist, and separately exercise a real recovery session through
password saving and login. The account is deleted in `finally`. Existing users,
passwords and project settings are never modified. No email is sent: this
verifies link handling and Auth behavior, not mailbox delivery or a customized
email template. Perform a mailbox delivery smoke test before launch.
