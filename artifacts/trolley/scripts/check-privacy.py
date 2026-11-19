#!/usr/bin/env python3
"""Run rollback-only privacy checks without putting credentials in arguments."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import parse_qs, unquote, urlparse

PROJECT = "wtwkgdfacbnqsmhrespt"
ROOT = Path(__file__).resolve().parents[1]
url = os.environ.get("SUPABASE_TEST_DATABASE_URL", "")
try:
    parsed = urlparse(url)
    host = parsed.hostname or ""
    user = unquote(parsed.username or "")
    direct = host == f"db.{PROJECT}.supabase.co" and user == "postgres"
    pooler = host.endswith(".pooler.supabase.com") and user == f"postgres.{PROJECT}"
    sslmode = parse_qs(parsed.query).get("sslmode", ["require"])
    if not (
        parsed.scheme in ("postgres", "postgresql")
        and (direct or pooler)
        and parsed.path == "/postgres"
        and sslmode[0] in ("require", "verify-ca", "verify-full")
        and not set(parse_qs(parsed.query)) - {"sslmode"}
    ):
        raise ValueError("Target is not the authorized test project's postgres connection.")
except ValueError:
    sys.exit("Refusing connection: use the authorized project's direct or session-pooler PostgreSQL URI.")
if not url:
    sys.exit("SUPABASE_TEST_DATABASE_URL is required in Secrets.")

# Set individual libpq fields: PGDATABASE does not expand a URI like -d does.
# Remove inherited routing/options so the workspace database cannot be selected.
env = {key: value for key, value in os.environ.items() if not key.startswith("PG")}
env.update({
    "PGDATABASE": "postgres", "PGHOST": host, "PGPORT": str(parsed.port or 5432),
    "PGUSER": user, "PGPASSWORD": unquote(parsed.password or ""),
    "PGSSLMODE": sslmode[0], "PGCONNECT_TIMEOUT": "15",
})


def run(args, sql=None):
    result = subprocess.run(
        ["psql", "-X", "--no-password", "--quiet", "--tuples-only", "--no-align",
         "--set=ON_ERROR_STOP=1", *args],
        input=sql, text=True, capture_output=True, env=env,
    )
    # Defense in depth: even malformed-URI diagnostics must not reveal a secret.
    for stream in (result.stdout, result.stderr):
        safe = stream.replace(url, "[redacted connection]")
        if parsed.password:
            safe = safe.replace(parsed.password, "[redacted]").replace(
                unquote(parsed.password), "[redacted]")
        print("\n".join(line for line in safe.splitlines() if line.strip()))
    return result.returncode


def main():
    print(f"Authorized target: {PROJECT}; TLS required.")
    if sys.argv[1:] == ["--inspect"]:
        return run([], """
begin read only;
select 'connection', current_database(), current_user;
select 'rls', c.relname, c.relrowsecurity from pg_class c
join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and c.relkind='r' order by c.relname;
select 'policy', tablename, policyname, cmd, qual, with_check
from pg_policies where schemaname='public'
and tablename in ('profiles','user_roles','provider_profiles','services',
'verification_requests','conversations','conversation_participants','messages')
order by tablename, policyname;
select 'trigger', c.relname, t.tgname, pg_get_triggerdef(t.oid)
from pg_trigger t join pg_class c on c.oid=t.tgrelid
join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and not t.tgisinternal order by c.relname,t.tgname;
rollback;
""")
    if sys.argv[1:]:
        sys.exit("Usage: check-privacy.py [--inspect]")
    return run(["--file", str(ROOT / "supabase/tests/privacy_roles.sql")])


if __name__ == "__main__":
    sys.exit(main())
