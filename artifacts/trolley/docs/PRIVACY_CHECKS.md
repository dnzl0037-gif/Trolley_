# Role-based Supabase privacy checks

## Safety and current status

The owner authorized `wtwkgdfacbnqsmhrespt` as the dedicated test project for
temporary security fixtures. This is not permission to test another project.
Role-based checks ran successfully on **2026-11-19** using a trusted PostgreSQL
connection with TLS. The public browser key and the existing single test login
were not used to establish admin fixtures or bypass RLS during assertions.

Never run the initial migrations as a way of preparing an unknown database.
The suite tests the installed schema; it does not install or repair it.

## Reproduce

1. Confirm the connection belongs to the authorized test project. Use the
   Supabase **Connect** dialog to obtain a PostgreSQL connection string.
   A session-pooler connection is suitable when direct IPv6 is unavailable.
   Store it as `SUPABASE_TEST_DATABASE_URL` in workspace Secrets, never in chat,
   source files, terminal command arguments, screenshots, or reports.
2. Use a trusted `postgres` connection that can insert synthetic Auth records
   and `SET ROLE anon/authenticated`. Do not use a service-role JWT as the test
   identity: that would bypass RLS and invalidate the results.
3. Inspect the installed policies, then run the checks:

   ```sh
   python3 artifacts/trolley/scripts/check-privacy.py --inspect
   python3 artifacts/trolley/scripts/check-privacy.py
   ```

   The runner requires TLS, validates the authorized project hostname/username,
   passes credentials only through the child process environment, and enables
   psql stop-on-error. It never applies migrations.
   Alternatively, paste the entire `supabase/tests/privacy_roles.sql` file into the authorized test project's SQL
   Editor and run it as one batch. After any error, issue `ROLLBACK` before doing
   anything else on that connection.
4. Save the labelled results and summary. A failed check raises an error and
   must never be reported as a passing audit.

The script creates randomly identified synthetic users using invalid-domain
emails, without sending signup emails. Profiles are initialized by the installed
signup trigger. Its helpers are temporary and **security invoker**. Checks run
with the actual `anon` and `authenticated` database roles and matching JWT claims.
Admin status is assigned only by the trusted fixture setup, not by JWT metadata.
All fixtures, successful writes, helper functions and attempted forbidden writes
are rolled back. Existing account data is neither selected nor changed by the
role checks; queries are scoped to generated fixture IDs.

## Coverage

- Anonymous private account/profile, draft, unapproved listing, location, image,
  verification-document, conversation and message visibility.
- Customer and provider isolation with positive own-record controls.
- Drafts belonging to both unpublished and approved public providers.
- Customer self-promotion, direct provider-profile insertion, provider
  self-approval, completion, ownership transfer and listing publication.
- Pending verification submission versus forged review/approval fields.
- Two independent conversations: own membership must not grant access to the
  other conversation's messages or permit writes to it.
- Sender spoofing, direct membership insertion, unrelated inquiry RPC access,
  legitimate conversation RPC access, legitimate message sending.
- Admin reads and moderation with the normal authenticated database role.
  Admin message sending still requires membership.

This is a database authorization check, not a full application, email, Storage
bucket, payment, concurrency or launch-readiness audit.

## Observed results

The read-only inspection found RLS enabled on all 25 public application tables
and the original two migrations' policies/triggers installed. PostgreSQL's
stored policy expressions confirmed that the original message policies compared
`cp.conversation_id = cp.conversation_id` rather than correlating to the message.

| Run | Passed | Failed | Result |
| --- | ---: | ---: | --- |
| Installed original policies | 51 | 5 | Two flaws demonstrated with disposable fixtures |
| After targeted third migration, expanded suite | 69 | 0 | All tested role boundaries and legitimate operations passed |

The five original failing assertions were:

- Customer reading a message from an unrelated conversation.
- Customer sending a message to an unrelated conversation.
- Provider reading a message from an unrelated conversation.
- Provider sending a message to an unrelated conversation.
- Provider inserting a verification request with forged approval/reviewer fields.

Both actors already belonged to a different conversation, which is essential to
reproducing the message flaw. Just testing a user with no memberships would miss it.
Unexpectedly allowed forbidden writes were individually rolled back by the test
helper, and the failing test transaction was rolled back on connection close.

Only `202611190003_message_and_verification_privacy.sql` was applied, atomically,
to the owner-authorized test project. The initial schema and seed were not
rerun. The original two migration files remain unchanged. The targeted migration:

- Qualifies `messages.conversation_id` in message read/send policies.
- Requires provider verification inserts to be pending, with no reviewer,
  reviewer note or review timestamp. The separate admin policy still allows
  administrative review.

The expanded passing run also checked each protected verification field
independently, cross-provider evidence submission, real conversation creation
through the RPC, repeat calls without duplicates, both intended memberships,
and positive admin review/insert operations.

All 69 assertions used `anon` or `authenticated` except two final fixture
integrity checks, which inspected the newly created RPC conversation and its
memberships as the trusted setup role. Admin assertions used `authenticated`
with a fixture admin entry in `user_roles`, not a database role that bypasses RLS.

The passing run ended with explicit `ROLLBACK`. A subsequent read-only cleanup
check confirmed no synthetic privacy-test Auth users remained. No real users
were signed in, modified or deleted. No production project was tested or certified.

The new-installation SQL download includes the third migration; the README
lists it in installation order. Existing installations must apply only this
missing migration after confirming their target and current schema.

Application checks also passed: `typecheck`, `lint`, and `build` for
`@workspace/trolley`. After one workflow restart, the homepage rendered without
browser errors. The setup download returned HTTP 200, contained the new policies
after the initial schema, and rejected POST with HTTP 405. It remains a
download-only helper, not a database execution endpoint.
