# Trolley import into Replit

The application was imported from the supplied Trolley ZIP, not rebuilt.
Its React 18, TypeScript, Vite 6, CSS, logo, marketplace interface, and
Supabase client remain the foundation. All files under `src/`, `public/`,
and `supabase/` are byte-for-byte identical to the archive.

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

No database migrations were executed or changed. No existing database was
replaced. Both migrations, RLS policies, and category seed SQL were preserved.
The ZIP did not include live Supabase credentials, and none were configured
in Replit during import.

Discovery and category browsing work without a database connection, as
designed by the original application. Authentication and provider draft
persistence require `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`.
Use only the public/publishable or anon key in this browser application,
never a service-role key.

Apply migrations and seed data only to the intended Supabase project, after
checking what has already been applied. Configure allowed auth redirect URLs
for the Replit preview and any later published domain. See the original README
for the original setup instructions and known product boundaries.
