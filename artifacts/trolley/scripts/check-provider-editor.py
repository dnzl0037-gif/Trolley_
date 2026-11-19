#!/usr/bin/env python3
"""Owner-authorized dedicated-project tests; fixtures always roll back."""
from pathlib import Path
import runpy
import sys

root = Path(__file__).resolve().parents[1]
# Reuse the existing strict dedicated-project connection guard and safe output.
guard = runpy.run_path(str(root / "scripts/check-privacy.py"))
migration = (root / "supabase/migrations/202611190004_provider_draft_editor.sql").read_text()
if sys.argv[1:] == ["--apply"]:
    sql = "begin;\n" + migration + "\nnotify pgrst, 'reload schema';\ncommit;"
elif not sys.argv[1:]:
    sql = "begin;\n" + migration + "\n" + (root / "supabase/tests/provider_editor.sql").read_text() + "\nrollback;"
else:
    sys.exit("Usage: check-provider-editor.py [--apply]")
sys.exit(guard["run"]([], sql))
