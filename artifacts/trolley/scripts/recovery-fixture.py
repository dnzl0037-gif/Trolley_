#!/usr/bin/env python3
"""Private test helper: create/delete one synthetic account, never print credentials."""
import hashlib
import json
import os
import subprocess
import sys
import uuid
from urllib.parse import parse_qs, unquote, urlparse


def main():
    project = "wtwkgdfacbnqsmhrespt"
    parsed = urlparse(os.environ.get("SUPABASE_TEST_DATABASE_URL", ""))
    user = unquote(parsed.username or "")
    host = parsed.hostname or ""
    public = urlparse(os.environ.get("VITE_SUPABASE_URL", ""))
    if not (
        public.hostname == f"{project}.supabase.co"
        and parsed.scheme in ("postgres", "postgresql")
        and parsed.path == "/postgres"
        and ((host == f"db.{project}.supabase.co" and user == "postgres")
             or (host.endswith(".pooler.supabase.com") and user == f"postgres.{project}"))
        and not set(parse_qs(parsed.query)) - {"sslmode"}
        and parse_qs(parsed.query).get("sslmode", ["require"])[0] in ("require", "verify-ca", "verify-full")
    ):
        sys.exit("Refusing to test outside the authorized dedicated Supabase project.")
    env = {key: value for key, value in os.environ.items() if not key.startswith("PG")}
    env.update(PGDATABASE="postgres", PGHOST=host, PGPORT=str(parsed.port or 5432),
               PGUSER=user, PGPASSWORD=unquote(parsed.password or ""),
               PGSSLMODE="require", PGCONNECT_TIMEOUT="15")
    payload = json.load(sys.stdin)
    fixture_id = str(uuid.UUID(payload["id"]))
    email = f"recovery-{fixture_id}@example.invalid"
    if sys.argv[1:] == ["create"]:
        token_hash = hashlib.sha224(uuid.uuid4().bytes).hexdigest()
        sql = f"""
        begin;
        insert into auth.users (
          instance_id, id, aud, role, email, encrypted_password,
          email_confirmed_at, recovery_token, recovery_sent_at,
          confirmation_token, email_change, email_change_token_new,
          email_change_token_current, reauthentication_token,
          created_at, updated_at
        ) values (
          '00000000-0000-0000-0000-000000000000', '{fixture_id}',
          'authenticated', 'authenticated', '{email}', '',
          now(), '{token_hash}', now(), '', '', '', '', '', now(), now()
        );
        insert into auth.identities (id, user_id, provider_id, identity_data, provider, created_at, updated_at)
        values (gen_random_uuid(), '{fixture_id}', '{fixture_id}',
          jsonb_build_object('sub', '{fixture_id}', 'email', '{email}', 'email_verified', true),
          'email', now(), now());
        insert into auth.one_time_tokens (
          id, user_id, token_type, token_hash, relates_to, created_at, updated_at
        ) values (
          gen_random_uuid(), '{fixture_id}', 'recovery_token', '{token_hash}', '{email}', now(), now()
        );
        do $$
        begin
          if exists (select 1 from information_schema.columns
            where table_schema='auth' and table_name='one_time_tokens' and column_name='expires_at') then
            update auth.one_time_tokens set expires_at=now() + interval '5 minutes'
              where user_id='{fixture_id}';
          end if;
        end $$;
        commit;
        """
    elif sys.argv[1:] == ["delete"]:
        sql = f"delete from auth.users where id='{fixture_id}' and email='{email}';"
        token_hash = None
    else:
        sys.exit("Usage: recovery-fixture.py create|delete")
    result = subprocess.run(
        ["psql", "-X", "--no-password", "--quiet", "--set=ON_ERROR_STOP=1"],
        input=sql, capture_output=True, text=True, env=env,
    )
    if result.returncode:
        # Database errors may echo SQL containing a recovery token. Do not print.
        sys.exit("Unable to prepare/clean up the isolated recovery fixture.")
    print(json.dumps({"email": email, "token_hash": token_hash}))


if __name__ == "__main__":
    main()
