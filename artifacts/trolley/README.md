# Trolley — Services Marketplace

Trolley is a mobile-first services marketplace that helps customers discover real professionals and helps service providers build trusted business profiles. Zimbabwe is the initial market; the data model supports international expansion.

## Current build

This is Trolley, the services-marketplace interpretation (Option A) of the supplied LocalDir master prompt. The shopping-cart image is retained as the requested brand logo; Trolley does not sell retail products. See `docs/REFERENCE_NOTES.md` for how the uploaded images and videos informed the build.

- React + TypeScript + Vite frontend
- Responsive landing page with light/dark themes
- Searchable service taxonomy: 51 parent families and 1,020 distinct subcategories (1,071 total category rows including parents)
- Customer email/password registration and sign-in through Supabase Auth
- Google OAuth entry point (requires provider configuration)
- Date-of-birth gate at 13 years; the database trigger independently rejects under-13 accounts
- Optional analytics consent UI; no analytics scripts are loaded in this build
- Provider profile draft creation through a server-side Supabase RPC
- Provider drafts are not public; admin approval is required before directory visibility
- PostgreSQL schema, RLS policies, security hardening migration and category seed data
- Core schema foundations for enquiries, conversations, quotations, quotation line items, jobs, blocks, notifications and subscriptions
- Capacitor configuration for a future Android wrapper

## Requirements

- Node.js 20 LTS (Node 18.18+ may work with the selected toolchain)
- npm
- A Supabase project for live authentication and persistence

## Run locally

```bash
npm install
cp .env.example .env
# Edit .env with your Supabase project URL and publishable/anon key
npm run dev
```

Open the local URL printed by Vite. Without Supabase credentials the discovery interface works, but account creation and provider draft persistence intentionally remain disabled.

## Supabase setup

1. Create a Supabase project.
2. In the SQL Editor, run `supabase/migrations/202611190001_initial_schema.sql`.
3. Run `supabase/migrations/202611190002_security_hardening.sql`.
4. Run `supabase/migrations/202611190003_message_and_verification_privacy.sql`.
5. Run `supabase/seed/001_categories.sql`.
6. Confirm the seed reports at least 1,000 child categories (the supplied seed contains 1,020).
7. In Authentication → URL Configuration, set the local and production Site URL / redirect allowlist.
8. Enable Google as an Auth provider and configure its OAuth client ID and secret in Supabase. Never put the Google client secret in this repository or in a `VITE_` variable.
9. Add `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` to `.env` locally and to the hosting provider's environment settings for production.

The Supabase publishable/anon key is intended for browser use; database RLS is the security boundary. Never use a service-role key in frontend code.

## Environment variables

| Variable | Purpose | Public in browser? |
|---|---|---|
| `VITE_SUPABASE_URL` | Supabase project URL | Yes |
| `VITE_SUPABASE_ANON_KEY` | Supabase publishable/anon key | Yes, protected by RLS |

Payment and AI secrets are deliberately not added to the frontend. Pesepay integration requires merchant credentials and a server-side Edge Function/webhook implementation before payments can be enabled.

## Android preparation

```bash
npm install
npm run build
npx cap add android
npx cap sync android
npx cap open android
```

Build and test on a physical Android device before describing the app as release-ready. Configure signing keys privately in Android Studio; do not commit them.

## Security notes

- `user_roles` has no client insert/update/delete policy. Admin assignment is server-controlled.
- Provider verification and completion fields are protected by a database trigger.
- Providers can create drafts and submit for review; only administrators can publish/reject/suspend/archive listings.
- Public directory visibility requires a complete, approved provider profile and a published service.
- Conversation membership cannot be self-assigned. `start_inquiry_conversation` checks that the caller is an enquiry participant.
- Reviews are only created against completed enquiries and provider responses cannot change rating/body/moderation status.
- Payment events are not writable by browser clients.
- Non-essential tracking is not loaded; the consent banner keeps optional analytics off unless accepted.

See [PRIVACY_CHECKS.md](docs/PRIVACY_CHECKS.md) for reproducible role-by-role checks
and the authorized test-project results. Testing a dedicated project does not
certify production or replace a full security audit. For an existing installation,
inspect its objects and migration history, then apply only missing migrations;
do not rerun the initial schema.

## Known implementation boundaries

This is the initial build foundation, not the finished production marketplace. The following need subsequent implementation/configuration: provider dashboard and full onboarding steps, customer/provider messaging UI, quotation/job workflow, verified-review moderation UI, admin dashboard, secure storage buckets and upload policies, notifications, Pesepay Edge Functions/webhooks, AI assistant server integration, automated tests, CI, monitoring, backup/restore validation, final legal policies, SEO sitemap and Android release signing.

No real provider listings, reviews, payment confirmations or usage statistics are fabricated.
