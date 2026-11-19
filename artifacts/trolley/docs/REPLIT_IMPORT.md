# Trolley import into Replit

The application was imported from the supplied Trolley ZIP, not rebuilt.
Its React 18, TypeScript, Vite 6, CSS, logo, marketplace interface, and
Supabase client remain the foundation. Files under `public/` and `supabase/`
are byte-for-byte identical to the archive. Subsequent account work added
session-aware navigation, artifact-aware auth callbacks, error handling, and
read-back of the signed-in owner's saved provider profile under `src/`.

## Import locations

- Application: `artifacts/trolley/`
- Original uploaded ZIP: `attached_assets/Trolley-Services-Marketplace_1795101084813.zip`
- Extracted reference copy: `.local/trolley-import/trolley/`
- Unused registration scaffold, retained rather than deleted:
  `.local/trolley-scaffold/`

The workspace's other applications and shared libraries were not replaced.
The root dependency lockfile was updated by pnpm.

## Necessary configuration adjustments

- The package is named `@workspace/trolley` for workspace commands.
- Vite listens on `0.0.0.0`, uses the assigned `PORT` and `BASE_PATH`,
  and accepts Replit preview hosts.
- Development dependency optimization, source transformation, and builds use
  the ES2022 target to avoid incompatibilities between Vite 6's older default
  browser targets and the workspace's secured esbuild version.
- `typecheck` explicitly checks `tsconfig.app.json`; the original command
  targeted a solution configuration with no source files.
- TypeScript resolves React types locally to preserve React 18 despite
  other workspace applications using React 19.
- An ESLint flat configuration was added because the ZIP included a lint
  command and ESLint 9 but no configuration.
- With user approval, Capacitor core and CLI were updated from version 6
  to version 8 because the older CLI depended on blocked `tar` packages.
  The obsolete `bundledWebRuntime: false` property was removed; the app ID,
  app name, `dist` web directory, and Android HTTPS scheme are unchanged.
- Android scripts use pnpm's installed CLI instead of downloading a CLI
  through npx.
- Replit's static output directory points to the original Vite `dist`
  directory, also used by Capacitor.

## Verification

Source type checking, linting, and the production web build passed during
import. The development server started successfully, returned HTTP 200,
and the original homepage rendered without browser errors.
No Android app was generated or tested.

## Supabase remains external

No existing database was replaced. Both original migrations, RLS policies,
and category seed SQL were preserved. After confirming the target and absent
Trolley objects/migration history, the owner ran the combined setup script
successfully in the existing Supabase project's SQL Editor.
The ZIP did not include live Supabase credentials, and none were configured
in Replit during import. The project URL and public publishable key were later
supplied through secure configuration. The owner subsequently corrected the
project URL, and the saved public key is now accepted by Supabase Auth.
Email/password authentication and email confirmation are enabled; Google
sign-in is disabled. Following installation, read-only database API checks
confirmed that the provider table is available and categories contain
51 parent families and 1,020 subcategories.

### Live account verification

The browser verification used a dedicated test account:

- Signup returned successfully and displayed the check-email notice.
- The owner confirmed receipt and followed the email link back to Trolley,
  verifying the preview redirect path.
- Password sign-in succeeded and showed the signed-in navigation.
- `create_provider_profile` succeeded once. Authenticated read-back confirmed
  an incomplete, unverified provider profile, one `draft` service in the chosen
  category, and the submitted city/country location.
- The same saved profile loaded after a browser refresh and after signing out
  and signing back in, without creating a duplicate.
- Signed-out navigation and the provider entry point showed no prior profile.
- The browser finished signed out; the clearly labelled private test draft
  was left in place as persistence evidence. It was not published.

Type checking, linting, and the production build also passed after the account
changes. This verifies the account/draft journey, not a broader role-by-role
privacy audit or launch-readiness review. Google OAuth and a future published
domain still require their own provider/domain configuration.

Discovery and category browsing work without a database connection, as
designed by the original application. Authentication and provider draft
persistence require `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`.
Use only the public/publishable or anon key in this browser application,
never a service-role key.

Apply migrations and seed data only to the intended Supabase project, after
checking what has already been applied. Configure allowed auth redirect URLs
for the Replit preview and any later published domain. See the original README
for the original setup instructions and known product boundaries.

### Existing-project setup checklist

For an owner-confirmed fresh installation, the development server provides
`<artifact-base-path>__setup/trolley-database.sql` as a downloadable setup file.
It combines both unchanged migrations and the unchanged category seed contents
inside one transaction (replacing only the seed's separate transaction wrapper).
Open the downloaded file, copy its full contents into the confirmed project's
Supabase SQL Editor, and run once. This endpoint only prepares a download:
it never executes SQL, contains no credentials, and is not in the production
build. If installation fails, stop and inspect the error instead of deleting
tables or blindly rerunning it.

Do not rerun the initial-schema migration against an unknown or partially
configured database. It contains non-idempotent table and enum creation.
First confirm the intended project and inspect both actual objects and any
recorded migration history in the Supabase SQL Editor:

```sql
select
  to_regclass('public.categories') as categories,
  to_regclass('public.provider_profiles') as provider_profiles,
  to_regprocedure('public.create_provider_profile(text,text,text,text,text)') as provider_rpc,
  to_regclass('supabase_migrations.schema_migrations') as migration_history;
```

If the history table exists, inspect its versions:

```sql
select version from supabase_migrations.schema_migrations order by version;
```

SQL Editor installations may have application objects without CLI migration
history. Resolve any partial or mismatched installation before running scripts.
Only after that check, apply missing migrations in order and the category seed
to the confirmed target. Preserve both original migration files and seed data.
The seed contains 51 parent categories and 1,020 children.

In Supabase Authentication → URL Configuration, add the exact preview origin
plus the artifact base path to the redirect allowlist, retaining existing URLs.
Email confirmations and Google OAuth now use the same artifact-aware callback.
The browser publishable key cannot inspect migration history or change auth
URL configuration. Those actions require the Supabase project owner.
