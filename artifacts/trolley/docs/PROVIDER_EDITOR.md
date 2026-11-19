# Provider draft editing and submission

The **My business** form reads the account's stored business description,
service category, location, listing status and verification state. It supports
the single service/location created by onboarding. Other listing states and
multi-service/location profiles are explicitly read-only rather than guessing
which records to overwrite.

**Save changes** calls `update_provider_draft`, then reloads the records from
Supabase. **Submit for review** saves those same fields atomically and changes
the service from `draft` to `pending_review`, setting `submitted_at`.
Saving pending-review edits preserves that state. Blank descriptions clear the
stored description. Neither operation changes verification, profile completion,
or publication approval. Existing RLS and database guards remain in effect.

## Existing external Supabase installations

Apply only the new additive migration
`supabase/migrations/202611190004_provider_draft_editor.sql` after the existing
migrations, using the normal trusted Supabase migration process. Do not rerun
the initial schema or replace the external database. Reload the PostgREST schema
cache after applying (`notify pgrst, 'reload schema';`).
The development setup download includes this migration for fresh installations.
If it is missing, saves show the Supabase error; they do not silently fall back
to non-atomic writes.

The migration was applied only to the previously authorized dedicated test
project, not to another database or a production deployment.

## Verification

From the repository root:

```sh
python3 artifacts/trolley/scripts/check-provider-editor.py
pnpm --filter @workspace/trolley run typecheck
pnpm --filter @workspace/trolley run lint
pnpm --filter @workspace/trolley run build
```

The SQL runner strictly checks the dedicated test target, installs the function
inside a rollback-only transaction, and tests owner edits, related-record
read-back, invalid-category atomicity, pending-review submission, optional-field
clearing, unrelated-owner/anonymous denial, and blocked self-approval/publication.
It is scoped to the editor, not the separate privacy audit.

Browser tests, from `artifacts/trolley` with the managed web workflow running:

```sh
pnpm exec playwright test tests/provider-editor.spec.ts
TROLLEY_LIVE_PROVIDER=1 pnpm exec playwright test tests/provider-editor-live.spec.ts
```

The opt-in live test requires the editor migration on the authorized dedicated
project. It creates an isolated synthetic account, edits a real profile, checks
persistence in a fresh browser context, submits for review, checks reload, and
deletes its fixture account and related records in cleanup. It never changes an
existing account or sends email. The mocked tests cover mutation/read-back
errors and unsupported profile states.
