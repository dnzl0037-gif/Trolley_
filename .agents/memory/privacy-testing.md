---
name: Authorized privacy testing
description: Scope and safety constraints for Supabase role-based privacy testing.
---

The owner confirmed the configured external Supabase project as the dedicated
test project and authorized temporary test accounts and records for privacy checks.
This authorization does not extend to another project or to production.

**Why:** The requested audit explicitly requires a dedicated authorized target;
the presence of working application credentials alone was not authorization.

**How to apply:** Confirm connection identity before executing tests. Prefer
transaction-scoped fixtures with rollback. Do not rerun migrations blindly or
replace the external database.

Authorization predicates with nested queries must explicitly qualify outer
column references, and membership tests must give the adversarial actor their
own unrelated membership.

**Why:** An unqualified conversation identifier resolved to the inner table,
turning a per-conversation restriction into “belongs to any conversation.”
Testing only actors with no memberships would not reveal that mistake.

**How to apply:** Include at least two independent conversations and attempt
cross-conversation reads and writes under the normal authenticated role.
