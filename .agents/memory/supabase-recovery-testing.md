---
name: Supabase recovery testing
description: Live recovery test fixtures must match the dedicated project's current Auth token storage.
---

When creating isolated recovery test fixtures in the authorized dedicated
Supabase project, do not rely on older examples that set only a user's recovery
token and sent timestamp. Its Auth service also requires a matching one-time
token record, including expiration where the installed version supports it.

**Why:** A freshly created fixture with only the legacy user fields was rejected
as expired. The Auth implementation uses the one-time token record to find the
user; the apparent expiry error did not mean the fixture's timestamp was old.

**How to apply:** Use the maintained isolated recovery test helper, verify the
current Auth implementation when token behavior changes, and clean up fixtures.
Do not interpret a test fixture's invalid token as an application defect.
