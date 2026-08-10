# Agent Safety Net Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three scoped ops subagents (GCP, DB, GitHub) plus a repo-wide `PreToolUse` guardrail hook that hard-blocks credential access and destructive commands, auto-allows a narrow set of safe reads/approved writes, and defers everything else to the normal permission prompt.

**Architecture:** A single Python hook script (`.claude/hooks/guard.py`), registered in `.claude/settings.json` against the `Bash` tool, dispatches every intercepted call through five ordered policy checkers (`secrets` → `destructive` → `gcp` → `db` → `github`); the first checker to return a verdict wins, and an unmatched call defers to Claude Code's normal permission flow. The database agent talks to Postgres directly via `psql`/`pg_dump`/Flyway through `Bash` — no MCP server, no third-party dependency, one enforcement surface (this hook) instead of two.

**Tech Stack:** Python 3 stdlib only (`re`, `json`, `unittest` — no new dependencies), Claude Code hooks (`PreToolUse`).

## Global Constraints

- No secrets in committed files (CLAUDE.md).
- Always ask before `git commit`/`push`/PR creation (CLAUDE.md) — every "Commit" step below is something to propose and wait for approval on, not execute unattended.
- Follow the design spec exactly: `docs/superpowers/specs/2026-08-07-agent-safety-net-design.md`, as amended by this plan's decision to drop the `dbhub` MCP server in favor of direct `psql`/`pg_dump`/Flyway access (confirmed with the user during planning — fewer moving parts, no `npx`-pulled third-party code in the loop, and the hook-level `db.py` policy already had to handle raw `psql`/`pg_dump` text regardless). Do not add capabilities beyond what the design doc lists.
- `.claude/settings.json` **and** `.claude/settings.local.json` are both in this repo's `.gitignore` (only `.claude/agents/` and `.claude/commands/` are tracked under `.claude/`) — confirmed via `git check-ignore -v`. This means the hook *registration* (Task 8) is local machine config and is never committed; only the hook's actual code (`.claude/hooks/**`) and the agent definitions (`.claude/agents/**`) are trackable and get committed. This matches the project's existing convention, not a gap introduced by this plan.
- Known residual limitation (document, don't try to solve here): the hook inspects the literal Bash command text. A raw `psql` invocation whose connection string comes from an env var rather than a literal `localhost`/`127.0.0.1:5433` substring cannot be host-verified from text alone, so such write attempts are treated as non-local (denied) — this is intentionally conservative, not a gap to close.
- Known residual limitation (document, don't try to solve here — confirmed during Task 6's review, applies equally to every policy module built in Tasks 2–6): every deny/allow pattern is a plain `.search()` over the *entire* raw command string, with no shell-token or quoting awareness. A deny-list keyword appearing inside an unrelated quoted argument (e.g. `gh issue comment 5 --body "please gh pr merge this later"`) or as a delimited substring of a branch/identifier name (e.g. `git push origin feature/main-cleanup` tripping the "push to main" rule) can be wrongly denied even though the actual command is benign. This fails toward *over*-blocking, never under-blocking — it can produce a false "denied" that costs a retry, never a false "allowed" that bypasses the guard — so it is accepted as a known limitation rather than a security gap. A proper fix (tokenizing with `shlex.split` and matching only the actual command/subcommand structure, not embedded argument text) would need to touch all five modules and is out of scope for this plan.
- Known residual limitation (document, don't try to solve here — confirmed during Task 9's review): `db.py`'s Flyway-migration allow branch cannot verify what its actual target database is. It denies an *explicit* `spring.profiles.active=prod` declaration (`-D`/`--`/`--args` forms), but the equivalent `SPRING_PROFILES_ACTIVE=prod` env-var-prefix form isn't textually distinguishable from a harmless env-var assignment and isn't caught; more fundamentally, a bare `./gradlew flywayMigrate` with no profile flag at all is auto-allowed on the assumption it targets local dev, but the true determinant is whatever `DATABASE_URL` resolves to in the ambient shell environment at execution time (`backend/src/main/resources/application.yml`'s `spring.datasource.url: ${DATABASE_URL}` has no default, and no `application-prod.yml` exists) — invisible from command text alone. This is the same category as the `psql`-env-var-host limitation above, just for Flyway's implicit-profile case specifically.
- Known residual limitation (document, don't try to solve here — confirmed during Task 9's review): `db.py`'s `pg_dump`/`pg_restore` host check (`_LOCAL_HOST_RE.search(text)`) scans the whole command string for a `localhost`/`127.0.0.1:5433` substring rather than verifying it's the actual `-h`/`--host` value of that specific invocation. A remote `pg_dump`/`pg_restore` chained after a local one (`pg_dump -h localhost ... && pg_restore -h prod-db.supabase.co ...`), or accompanied by an unrelated `localhost` token elsewhere in the same command (e.g. a trailing shell comment), is wrongly allowed. Closing this properly needs the same per-invocation anchoring `secrets.py`'s `DB_CLIENT_DSN_ARG_RE` eventually converged on for `psql` after six rounds (see Task 7's ledger entries and `task-7-report.md`) — deferred as a known gap rather than a sixth application of that fix pattern in this plan.

---

### Task 1: Remove the dbhub MCP server

**Files:**
- Delete: `.mcp.json`
- Modify: `.claude/settings.local.json` (not committed — see Global Constraints — but still worth cleaning up locally so Claude Code doesn't try to start a server pointing at a deleted config)

**Interfaces:**
- None — this task only removes things. Later tasks (`db.py` in Task 5, `db-agent.md` in Task 9) are written to talk to Postgres via `psql`/`pg_dump`/Flyway only, with no MCP tool involved.

- [ ] **Step 1: Confirm current contents before deleting**

```bash
cat .mcp.json
cat .claude/settings.local.json
```

Expected (this repo's current state): `.mcp.json` defines a single `postgres` server running `npx -y @bytebase/dbhub@0.24.0 --transport stdio` with `DSN` from `${FASHION_RENTAL_DB_URL}`; `.claude/settings.local.json` contains `{"enabledMcpjsonServers": ["postgres"]}`. If either file has since gained unrelated content, stop and re-scope this task with the user rather than deleting it blind.

- [ ] **Step 2: Delete `.mcp.json`**

```bash
git rm .mcp.json
```

- [ ] **Step 3: Remove the now-dangling reference in `.claude/settings.local.json`**

Change:
```json
{
  "enabledMcpjsonServers": [
    "postgres"
  ]
}
```
to:
```json
{}
```
This file is gitignored, so this edit is local-only and has no commit step.

- [ ] **Step 4: Restart Claude Code (or start a fresh session) and confirm no MCP startup error**

Hooks/MCP server config is read at session start. In the new session, confirm there's no error about a missing or failing `postgres` MCP server (there shouldn't be any `mcp__postgres__*` tools listed at all now).

- [ ] **Step 5: Commit**

Ask the user for approval first (CLAUDE.md). If approved:

```bash
git commit -m "$(cat <<'EOF'
chore(mcp): remove the dbhub postgres MCP server

The DB agent talks to Postgres directly via psql/pg_dump/Flyway through
Bash instead — one less third-party, npx-pulled dependency in the loop,
and the guard hook's db.py policy already had to handle raw psql text
regardless of whether an MCP tool also existed.
EOF
)"
```

---

### Task 2: Secrets policy module

**Files:**
- Create: `.claude/hooks/policy/__init__.py` (empty)
- Create: `.claude/hooks/policy/secrets.py`
- Create: `.claude/hooks/tests/__init__.py` (empty)
- Create: `.claude/hooks/tests/test_secrets.py`

**Interfaces:**
- Produces: `secrets.check(tool_name: str, text: str, tool_input: dict) -> tuple[str, str] | None`. Returns `("deny", reason)` or `None` (never `"allow"` — secrets policy only ever blocks or stays silent). Every other policy module in this plan implements the same `check(tool_name, text, tool_input)` signature.

- [ ] **Step 1: Write the failing tests**

`.claude/hooks/tests/test_secrets.py`:

```python
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import secrets


class SecretsPolicyTest(unittest.TestCase):
    def test_denies_bare_printenv(self):
        result = secrets.check("Bash", "printenv", {})
        self.assertEqual(result[0], "deny")

    def test_denies_bare_env(self):
        result = secrets.check("Bash", "env", {})
        self.assertEqual(result[0], "deny")

    def test_denies_dotenv_file_read(self):
        result = secrets.check("Bash", "cat backend/.env", {})
        self.assertEqual(result[0], "deny")

    def test_denies_secret_named_var_echo(self):
        result = secrets.check("Bash", "echo $JWT_SECRET", {})
        self.assertEqual(result[0], "deny")

    def test_denies_database_url_var_echo(self):
        result = secrets.check("Bash", "echo ${SUPABASE_DATABASE_URL}", {})
        self.assertEqual(result[0], "deny")

    def test_denies_gcloud_secrets_access(self):
        result = secrets.check(
            "Bash", "gcloud secrets versions access latest --secret=jwt", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_gh_secret_list(self):
        result = secrets.check("Bash", "gh secret list", {})
        self.assertEqual(result[0], "deny")

    def test_denies_docker_inspect(self):
        result = secrets.check(
            "Bash", "docker inspect fashion-rental-backend", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_proc_environ_read(self):
        result = secrets.check("Bash", "cat /proc/1234/environ", {})
        self.assertEqual(result[0], "deny")

    def test_allows_nonsensitive_var_echo(self):
        result = secrets.check("Bash", "echo $VITE_API_URL", {})
        self.assertIsNone(result)

    def test_allows_unrelated_command(self):
        result = secrets.check("Bash", "pnpm test", {})
        self.assertIsNone(result)

    def test_allows_database_url_var_as_psql_argument(self):
        result = secrets.check(
            "Bash", 'psql "$SUPABASE_DATABASE_URL" -c "SELECT 1"', {}
        )
        self.assertIsNone(result)

    def test_still_denies_database_url_var_echo_even_near_psql_word(self):
        result = secrets.check(
            "Bash", 'echo "using psql with $SUPABASE_DATABASE_URL"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_leak_chained_after_legitimate_psql_invocation(self):
        result = secrets.check(
            "Bash", "psql -h localhost && echo $JWT_SECRET", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_used_as_sql_text_via_flag_style_psql(self):
        result = secrets.check(
            "Bash", 'psql -h localhost -c "SELECT $JWT_SECRET"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_via_command_substitution_inside_psql_arg(self):
        result = secrets.check(
            "Bash",
            'psql -h localhost -c "SELECT 1" ; psql -h localhost -c "SELECT $(echo $JWT_SECRET)"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_as_sql_text_in_second_chained_psql(self):
        result = secrets.check(
            "Bash", 'psql -h localhost && psql -h remote -c "SELECT $SECRET_TOKEN"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_used_as_pg_dump_output_filename(self):
        result = secrets.check(
            "Bash", "pg_dump -h localhost -f $JWT_SECRET.sql", {}
        )
        self.assertEqual(result[0], "deny")

    def test_allows_database_url_var_with_pg_dump(self):
        result = secrets.check(
            "Bash", 'pg_dump "$SUPABASE_DATABASE_URL" > backup.sql', {}
        )
        self.assertIsNone(result)

    def test_denies_secret_via_prose_adjacent_to_psql_word(self):
        result = secrets.check(
            "Bash", 'echo "run psql $JWT_SECRET later"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_via_psql_word_after_separator_but_not_invoked(self):
        result = secrets.check(
            "Bash", "cat file.txt; echo psql $JWT_SECRET", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_via_psql_word_as_argument_to_other_command(self):
        result = secrets.check(
            "Bash", "foo psql $JWT_SECRET", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_on_own_line_after_bare_psql(self):
        result = secrets.check("Bash", "psql\n$JWT_SECRET", {})
        self.assertEqual(result[0], "deny")


if __name__ == "__main__":
    unittest.main()
```

> **Corrected during Task 7's review** (see the plan's execution ledger): the original 11-case test file above matched a `secrets.py` with no DB-client exemption at all, which turned out to conflict with `db.py`'s "reads allowed anywhere, including prod" guarantee — a production DSN can only be referenced via an env var (never hardcoded), and that env var's name inevitably matches this module's `DATABASE_URL`/`DSN` pattern. Closing that conflict without opening a new leak took six rounds (documented in full in the ledger and `task-7-report.md`): each attempt at a coarse "is this whole string/segment safe" classification let something adjacent slip through unscanned — a compound `&&`-chained command, a secret embedded in a `-c` SQL argument, the word "psql" appearing in unrelated prose, a secret on its own line after a bare invocation. The 12 tests added above lock in every one of those cases.

- [ ] **Step 2: Run tests to verify they fail**

```bash
python3 -m unittest .claude.hooks.tests.test_secrets -v
```

Expected: `ModuleNotFoundError: No module named 'policy'` (the module doesn't exist yet).

- [ ] **Step 3: Write `.claude/hooks/policy/__init__.py`**

Empty file.

- [ ] **Step 4: Write `.claude/hooks/policy/secrets.py`**

```python
import re

ENV_DUMP_RE = re.compile(r"(^|[;&|]\s*)(env|printenv|export)\s*(\s|;|&|\||$)")
DOTENV_FILE_RE = re.compile(
    r"\b(cat|less|more|head|tail|grep|vi|vim|nano|code|open)\b[^\n]*\.env(\.[a-zA-Z0-9_-]+)?\b"
)
KEY_FILE_RE = re.compile(
    r"\b(cat|less|more|head|tail|grep)\b[^\n]*(key\.json|service-account)"
)
SECRET_VAR_RE = re.compile(r"\$\{?([A-Za-z_][A-Za-z0-9_]*)\}?")
SECRET_NAME_RE = re.compile(
    r"(PASSWORD|SECRET|TOKEN|_KEY|KEY_ID|DSN|DATABASE_URL|_URL)", re.IGNORECASE
)
GCLOUD_SECRET_RE = re.compile(r"\bgcloud\s+secrets\s+(versions\s+access|describe)\b")
GH_SECRET_RE = re.compile(r"\bgh\s+secret\s+(list|get|set|delete)\b")
DOCKER_INSPECT_RE = re.compile(r"\bdocker\s+inspect\b|\bdocker\s+exec\b[^\n]*\benv\b")
PROC_ENVIRON_RE = re.compile(r"/proc/\S+/environ")
DB_CLIENT_DSN_ARG_RE = re.compile(
    r"(?:^|[;&|]\s*)(?:psql|pg_dump|pg_restore)[ \t]+[\"']?\$\{?[A-Za-z_][A-Za-z0-9_]*\}?[\"']?"
)

SAFE_VAR_NAMES = {
    "VITE_API_URL",
    "SPRING_PROFILES_ACTIVE",
    "NODE_ENV",
    "PATH",
    "HOME",
    "PWD",
}


def check(tool_name, text, tool_input):
    if not text:
        return None

    if ENV_DUMP_RE.search(text):
        return (
            "deny",
            "Bare env/printenv/export dumps the whole process environment, which may "
            "include secrets. Blocked by secrets policy.",
        )
    if DOTENV_FILE_RE.search(text) or KEY_FILE_RE.search(text):
        return (
            "deny",
            "Reading .env* files or key/service-account files directly is blocked by "
            "secrets policy.",
        )
    if GCLOUD_SECRET_RE.search(text):
        return ("deny", "gcloud secrets access is blocked by secrets policy.")
    if GH_SECRET_RE.search(text):
        return ("deny", "gh secret commands are blocked by secrets policy.")
    if DOCKER_INSPECT_RE.search(text):
        return (
            "deny",
            "docker inspect / docker exec ... env can leak container secrets (see "
            "infra/deployment-plan.md accepted risks); blocked by secrets policy.",
        )
    if PROC_ENVIRON_RE.search(text):
        return ("deny", "Reading /proc/*/environ is blocked by secrets policy.")

    exempt_spans = [m.span() for m in DB_CLIENT_DSN_ARG_RE.finditer(text)]

    for match in SECRET_VAR_RE.finditer(text):
        name = match.group(1)
        if name in SAFE_VAR_NAMES:
            continue
        if not SECRET_NAME_RE.search(name):
            continue
        start, end = match.span()
        if any(exempt_start <= start and end <= exempt_end for exempt_start, exempt_end in exempt_spans):
            continue
        return (
            "deny",
            f"Reference to ${{{name}}} matches a secret-like variable name "
            "pattern; blocked by secrets policy.",
        )

    return None
```

Note: `DB_CLIENT_DSN_ARG_RE` finds every span that looks like `psql "$VAR"` (binary name at command position — start of string or immediately after a `;`/`&`/`|` separator — followed only by non-newline whitespace and then a quoted/bare `$VAR`/`${VAR}`). A `SECRET_VAR_RE` match is only skipped if its exact character span falls entirely within one of those narrow spans. Nothing else — a `-c`/`-f` argument, a `$(...)` substitution, a second chained command, "psql" appearing as prose or as another command's argument, a secret on its own line after a bare invocation — can ever match that narrow pattern, so nothing else is ever exempted. This precision took six rounds to reach; see the note after the test file above and `task-7-report.md` for the failed intermediate attempts and why each one leaked.

- [ ] **Step 5: Write `.claude/hooks/tests/__init__.py`**

Empty file.

- [ ] **Step 6: Run tests to verify they pass**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_secrets.py" -v
```

Expected: `OK` — all 24 tests pass.

> **Further corrected during Task 9's review**: `SECRET_NAME_RE` above was broadened once more, from `r"(PASSWORD|SECRET|TOKEN|_KEY|KEY_ID|DSN|DATABASE_URL)"` to `r"(PASSWORD|SECRET|TOKEN|_KEY|KEY_ID|DSN|DATABASE_URL|_URL)"`, after `NEON_PG_URL` (named explicitly in `db-agent.md`'s prompt as a protected credential) turned out not to match any of the original alternatives. A `test_denies_neon_pg_url_var_echo` case was added. See `task-9-fix-report.md` for the full context, including two related, still-open gaps documented in Global Constraints (Flyway's implicit-profile and the `pg_dump`/`pg_restore` host check's whole-string scan).

- [ ] **Step 7: Commit**

Ask for approval first. If approved:

```bash
git add .claude/hooks/policy/__init__.py .claude/hooks/policy/secrets.py \
        .claude/hooks/tests/__init__.py .claude/hooks/tests/test_secrets.py
git commit -m "$(cat <<'EOF'
feat(hooks): add secrets-access policy module for the guard hook

First of five policy checkers the PreToolUse hook will dispatch through.
Blocks env dumps, .env/key-file reads, secret-manager reads, and any
reference to a secret-shaped variable name.
EOF
)"
```

---

### Task 3: Destructive-command policy module

**Files:**
- Create: `.claude/hooks/policy/destructive.py`
- Create: `.claude/hooks/tests/test_destructive.py`

**Interfaces:**
- Consumes: nothing from Task 2 (independent module, same `check(tool_name, text, tool_input)` signature).
- Produces: `destructive.check(...)`, used by `guard.py` in Task 7.

- [ ] **Step 1: Write the failing tests**

`.claude/hooks/tests/test_destructive.py`:

```python
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import destructive


class DestructivePolicyTest(unittest.TestCase):
    def test_denies_rm_rf_combined_flag(self):
        result = destructive.check("Bash", "rm -rf /opt/app/old", {})
        self.assertEqual(result[0], "deny")

    def test_denies_rm_separated_flags(self):
        result = destructive.check("Bash", "rm -r -f ./build", {})
        self.assertEqual(result[0], "deny")

    def test_allows_rm_force_only(self):
        result = destructive.check("Bash", "rm -f package-lock.json", {})
        self.assertIsNone(result)

    def test_denies_git_push_force(self):
        result = destructive.check("Bash", "git push --force origin main", {})
        self.assertEqual(result[0], "deny")

    def test_denies_git_push_short_force_flag(self):
        result = destructive.check("Bash", "git push -f origin main", {})
        self.assertEqual(result[0], "deny")

    def test_allows_plain_git_push(self):
        result = destructive.check("Bash", "git push origin feature/foo", {})
        self.assertIsNone(result)

    def test_denies_git_reset_hard(self):
        result = destructive.check("Bash", "git reset --hard HEAD~3", {})
        self.assertEqual(result[0], "deny")

    def test_denies_git_clean_force(self):
        result = destructive.check("Bash", "git clean -fd", {})
        self.assertEqual(result[0], "deny")

    def test_denies_git_branch_force_delete(self):
        result = destructive.check("Bash", "git branch -D old-feature", {})
        self.assertEqual(result[0], "deny")

    def test_denies_shutdown(self):
        result = destructive.check("Bash", "sudo shutdown -h now", {})
        self.assertEqual(result[0], "deny")

    def test_allows_unrelated_command(self):
        result = destructive.check("Bash", "git status", {})
        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_destructive.py" -v
```

Expected: `ModuleNotFoundError` or `AttributeError: module 'policy' has no attribute 'destructive'`.

- [ ] **Step 3: Write `.claude/hooks/policy/destructive.py`**

```python
import re

_SIMPLE_PATTERNS = [
    (
        re.compile(r"\bgit\s+push\b[^\n]*(--force\b|\s-f\b)"),
        "git push --force can overwrite remote history",
    ),
    (re.compile(r"\bgit\s+reset\s+--hard\b"), "git reset --hard discards local work irreversibly"),
    (re.compile(r"\bgit\s+clean\s+-\w*f\w*\b"), "git clean -f permanently deletes untracked files"),
    (re.compile(r"\bgit\s+branch\s+-D\b"), "git branch -D force-deletes a branch"),
    (re.compile(r"\bgit\s+filter-branch\b"), "git filter-branch rewrites history irreversibly"),
    (re.compile(r"\bdd\s+if="), "dd can overwrite raw disk devices"),
    (re.compile(r"\bmkfs(\.\w+)?\b"), "mkfs formats a filesystem, destroying its contents"),
    (re.compile(r"\b(shutdown|reboot)\b"), "shutdown/reboot is disruptive to a shared machine"),
    (re.compile(r"\bkill\s+-9\s+1\b"), "kill -9 1 kills PID 1 (init), crashing the machine"),
    (
        re.compile(r"\bchmod\s+-R\s+777\b"),
        "chmod -R 777 is an insecure, hard-to-reverse permission change",
    ),
]

_RM_SEGMENT_RE = re.compile(r"\brm\s+[^\n;&|]*")
_RECURSIVE_FLAG_RE = re.compile(r"(-[a-zA-Z]*r[a-zA-Z]*\b|--recursive\b)")
_FORCE_FLAG_RE = re.compile(r"(-[a-zA-Z]*f[a-zA-Z]*\b|--force\b)")


def _has_rm_rf(text):
    for match in _RM_SEGMENT_RE.finditer(text):
        segment = match.group(0)
        if _RECURSIVE_FLAG_RE.search(segment) and _FORCE_FLAG_RE.search(segment):
            return True
    return False


def check(tool_name, text, tool_input):
    if not text:
        return None
    if _has_rm_rf(text):
        return (
            "deny",
            "rm with both a recursive and a force flag is an irreversible bulk delete. "
            "Blocked by destructive-command policy.",
        )
    for pattern, why in _SIMPLE_PATTERNS:
        if pattern.search(text):
            return ("deny", f"{why}. Blocked by destructive-command policy.")
    return None
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_destructive.py" -v
```

Expected: `OK` — all 11 tests pass.

- [ ] **Step 5: Commit**

Ask for approval first. If approved:

```bash
git add .claude/hooks/policy/destructive.py .claude/hooks/tests/test_destructive.py
git commit -m "$(cat <<'EOF'
feat(hooks): add cross-cutting destructive-command policy module

Blocks rm -rf, git push --force, git reset --hard, git clean -f,
git branch -D, filter-branch, dd, mkfs, shutdown/reboot, kill -9 1,
chmod -R 777 — irreversible ops with no domain-specific home.
EOF
)"
```

---

### Task 4: GCP policy module

**Files:**
- Create: `.claude/hooks/policy/gcp.py`
- Create: `.claude/hooks/tests/test_gcp.py`

**Interfaces:**
- Produces: `gcp.check(...)`, used by `guard.py` in Task 7.

- [ ] **Step 1: Write the failing tests**

`.claude/hooks/tests/test_gcp.py`:

```python
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import gcp


class GcpPolicyTest(unittest.TestCase):
    def test_denies_instance_delete(self):
        result = gcp.check(
            "Bash",
            "gcloud compute instances delete fashion-rental-backend "
            "--zone=us-central1-a --quiet",
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_instance_stop(self):
        result = gcp.check(
            "Bash",
            "gcloud compute instances stop fashion-rental-backend --zone=us-central1-a",
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_address_delete(self):
        result = gcp.check(
            "Bash",
            "gcloud compute addresses delete fashion-rental-ip --region=us-central1 --quiet",
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_project_delete(self):
        result = gcp.check("Bash", "gcloud projects delete fashion-rental-123456", {})
        self.assertEqual(result[0], "deny")

    def test_denies_docker_rm(self):
        result = gcp.check("Bash", "docker rm fashion-rental-backend", {})
        self.assertEqual(result[0], "deny")

    def test_denies_docker_volume_prune(self):
        result = gcp.check("Bash", "docker system prune -af --volumes", {})
        self.assertEqual(result[0], "deny")

    def test_allows_instance_describe(self):
        result = gcp.check(
            "Bash",
            "gcloud compute instances describe fashion-rental-backend --zone=us-central1-a",
            {},
        )
        self.assertEqual(result[0], "allow")

    def test_allows_docker_logs(self):
        result = gcp.check("Bash", "docker logs --tail 200 fashion-rental-backend", {})
        self.assertEqual(result[0], "allow")

    def test_allows_approved_container_restart(self):
        result = gcp.check("Bash", "docker restart fashion-rental-backend", {})
        self.assertEqual(result[0], "allow")

    def test_defers_unrelated_command(self):
        result = gcp.check("Bash", "pnpm build", {})
        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_gcp.py" -v
```

Expected: `ModuleNotFoundError` or `AttributeError`.

- [ ] **Step 3: Write `.claude/hooks/policy/gcp.py`**

```python
import re

_DENY_PATTERNS = [
    (re.compile(r"\bgcloud\s+compute\s+instances\s+delete\b"), "deletes the GCP VM"),
    (
        re.compile(r"\bgcloud\s+compute\s+instances\s+stop\b"),
        "stopping the VM risks the static-IP billing edge case documented in "
        "infra/deployment-plan.md",
    ),
    (re.compile(r"\bgcloud\s+compute\s+addresses\s+delete\b"), "deletes the static IP"),
    (re.compile(r"\bgcloud\s+compute\s+firewall-rules\s+delete\b"), "deletes a firewall rule"),
    (re.compile(r"\bgcloud\s+compute\s+disks\s+delete\b"), "deletes a disk"),
    (re.compile(r"\bgcloud\s+projects\s+delete\b"), "deletes the GCP project"),
    (
        re.compile(r"\bgcloud\s+iam\s+service-accounts\s+(delete|keys\s+delete)\b"),
        "deletes an IAM identity or key",
    ),
    (re.compile(r"\bdocker\s+rm\b"), "removes a container"),
    (re.compile(r"\bdocker\s+stop\b"), "stops a container"),
    (
        re.compile(r"\bdocker\s+system\s+prune\b[^\n]*--volumes\b"),
        "prunes docker volumes, destroying data",
    ),
    (re.compile(r"\bdocker\s+volume\s+rm\b"), "removes a docker volume"),
    (re.compile(r"\bgcloud\s+compute\s+instances\s+create\b"), "creates/recreates a GCP VM — provisioning is a separate, manual workflow"),
    (re.compile(r"\bgcloud\s+compute\s+addresses\s+create\b"), "creates a static IP — provisioning is a separate, manual workflow"),
    (re.compile(r"\bgcloud\s+compute\s+firewall-rules\s+create\b"), "creates a firewall rule — provisioning is a separate, manual workflow"),
    (re.compile(r"\bgcloud\s+compute\s+disks\s+create\b"), "creates a disk — provisioning is a separate, manual workflow"),
    (re.compile(r"\bgcloud\s+iam\s+service-accounts\s+create\b"), "creates an IAM identity — provisioning is a separate, manual workflow"),
]

_ALLOW_PATTERNS = [
    re.compile(r"\bgcloud\s+compute\s+instances\s+(describe|list|get-serial-port-output)\b"),
    re.compile(r"\bgcloud\s+compute\s+(networks|firewall-rules)\s+(list|describe)\b"),
    re.compile(r"\bgcloud\s+billing\b"),
    re.compile(r"\bdocker\s+logs\b"),
    re.compile(r"\bdocker\s+ps\b"),
    re.compile(r"\bdocker\s+restart\s+fashion-rental-backend\b"),
]


def check(tool_name, text, tool_input):
    if not text:
        return None
    for pattern, why in _DENY_PATTERNS:
        if pattern.search(text):
            return ("deny", f"Blocked by GCP policy: this command {why}.")
    for pattern in _ALLOW_PATTERNS:
        if pattern.search(text):
            return ("allow", "Matches the GCP read/approved-write allow-list.")
    return None
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_gcp.py" -v
```

Expected: `OK` — all 12 tests pass.

> **Further corrected during Task 9's review**: the five `create`-verb deny entries above (instances/addresses/firewall-rules/disks/service-accounts) were added after the gcp-agent's prompt claim ("you must never attempt to... recreate the VM, its static IP, firewall rules, disks, or IAM identities") turned out to only be half-enforced — the original list only covered `delete`/`stop`. Two tests (`test_denies_instance_create`, `test_denies_firewall_rule_create`) were added; see `task-9-fix-report.md`.

- [ ] **Step 5: Commit**

Ask for approval first. If approved:

```bash
git add .claude/hooks/policy/gcp.py .claude/hooks/tests/test_gcp.py
git commit -m "$(cat <<'EOF'
feat(hooks): add GCP command policy module

Denies VM/IP/firewall/disk/project/IAM deletion and container removal;
allows read verbs and the one approved write (restarting the backend
container).
EOF
)"
```

---

### Task 5: DB policy module

**Files:**
- Create: `.claude/hooks/policy/db.py`
- Create: `.claude/hooks/tests/test_db.py`

**Interfaces:**
- Produces: `db.check(...)`, used by `guard.py` in Task 7. Applies only to `Bash` calls — `psql`/`pg_restore`/`pg_dump`/`./gradlew flywayMigrate`/`bootRun` text. There is no MCP tool in this design (Task 1 removed it), so this module never special-cases `tool_name`.

- [ ] **Step 1: Write the failing tests**

`.claude/hooks/tests/test_db.py`:

```python
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import db


class DbPolicyTest(unittest.TestCase):
    def test_always_denies_drop_regardless_of_host(self):
        result = db.check(
            "Bash",
            'psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c "DROP TABLE items"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_write_against_non_local_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "UPDATE items SET status=1"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_allows_select_via_psql_against_any_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "SELECT count(*) FROM items"',
            {},
        )
        self.assertEqual(result[0], "allow")

    def test_allows_flyway_migrate(self):
        result = db.check("Bash", "cd backend && ./gradlew flywayMigrate", {})
        self.assertEqual(result[0], "allow")

    def test_allows_local_pg_dump(self):
        result = db.check(
            "Bash",
            "pg_dump -h localhost -p 5433 -U fashion_user fashion_rental > backup.sql",
            {},
        )
        self.assertEqual(result[0], "allow")

    def test_denies_pg_dump_against_non_local_host(self):
        result = db.check("Bash", 'pg_dump "$NEON_PG_URL" | gzip > backup.sql.gz', {})
        self.assertEqual(result[0], "deny")

    def test_defers_unrelated_command(self):
        result = db.check("Bash", "pnpm test", {})
        self.assertIsNone(result)

    def test_defers_psql_with_no_recognizable_sql(self):
        result = db.check("Bash", "psql --version", {})
        self.assertIsNone(result)

    def test_defers_bare_interactive_psql_against_remote_host(self):
        result = db.check("Bash", 'psql "$SUPABASE_DATABASE_URL"', {})
        self.assertIsNone(result)

    def test_denies_insert_select_against_non_local_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "INSERT INTO items SELECT * FROM staging_items"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_delete_with_subselect_against_non_local_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "DELETE FROM items WHERE id IN (SELECT id FROM stale)"',
            {},
        )
        self.assertEqual(result[0], "deny")


if __name__ == "__main__":
    unittest.main()
```

> **Corrected during review** (see the plan's execution ledger): the first draft of this test file had 7 cases and the module below had a dead `_READ_KEYWORD_RE` with `not _WRITE_KEYWORD_RE.search(text)` used as a flawed "is this read-only" proxy. That let bare/version-only `psql` invocations — including an interactive session against a remote database with no `-c` argument at all — through unchecked. The fix below (checking write intent unconditionally before ever considering read intent) closes that gap; the 4 tests above were added to lock it in.

- [ ] **Step 2: Run tests to verify they fail**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_db.py" -v
```

Expected: `ModuleNotFoundError` or `AttributeError`.

- [ ] **Step 3: Write `.claude/hooks/policy/db.py`**

```python
import re

_READ_KEYWORD_RE = re.compile(r"\b(WITH|SELECT|EXPLAIN|SHOW)\b", re.IGNORECASE)
_WRITE_KEYWORD_RE = re.compile(
    r"\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE)\b", re.IGNORECASE
)
_ALWAYS_DENY_RE = re.compile(r"\b(DROP|TRUNCATE|ALTER|GRANT|REVOKE)\b", re.IGNORECASE)
_LOCAL_HOST_RE = re.compile(r"(localhost|127\.0\.0\.1)(:5433)?")
_PG_BACKUP_TOOL_RE = re.compile(r"\b(pg_dump|pg_restore)\b")
_SQL_TOOL_RE = re.compile(r"\bpsql\b")
_FLYWAY_ALLOW_RE = re.compile(r"\./gradlew\s+flywayMigrate\b")
_FLYWAY_PROD_DENY_RE = re.compile(r"\./gradlew\s+flywayMigrate\b[^\n]*spring\.profiles\.active=prod")
_BOOTRUN_DEV_ALLOW_RE = re.compile(r"\./gradlew\s+bootRun\b[^\n]*spring\.profiles\.active=dev")


def check(tool_name, text, tool_input):
    if not text:
        return None

    if _PG_BACKUP_TOOL_RE.search(text):
        if _LOCAL_HOST_RE.search(text):
            return ("allow", "Local dev pg_dump/pg_restore is permitted.")
        return (
            "deny",
            "pg_dump/pg_restore against a non-local database is reserved for the "
            "existing db-backup.yml workflow, not an agent.",
        )

    if not _SQL_TOOL_RE.search(text) and "gradlew" not in text:
        return None

    if _ALWAYS_DENY_RE.search(text):
        return (
            "deny",
            "DROP/TRUNCATE/ALTER/GRANT/REVOKE are never permitted via an agent, on any host.",
        )

    if _FLYWAY_PROD_DENY_RE.search(text):
        return (
            "deny",
            "Flyway migrations against the prod profile are not permitted via an agent.",
        )

    if _FLYWAY_ALLOW_RE.search(text) or _BOOTRUN_DEV_ALLOW_RE.search(text):
        return ("allow", "Local dev Flyway migration is an approved write action.")

    if _WRITE_KEYWORD_RE.search(text):
        if not _LOCAL_HOST_RE.search(text):
            return (
                "deny",
                "Write/DDL statements are only permitted against localhost:5433 (local dev); "
                "no literal local host was found in this command.",
            )
        return None

    if _READ_KEYWORD_RE.search(text):
        return ("allow", "Read-only SQL query.")

    return None
```

Note the ordering: write intent is checked and resolved (deny off-localhost, defer on-localhost) **before** read intent is ever considered. A statement containing both a write keyword and the word `SELECT` (e.g. `INSERT INTO items SELECT * FROM staging_items`, `DELETE FROM items WHERE id IN (SELECT id FROM stale)`) must be treated as a write, not short-circuited to "read-only" — reversing this ordering reintroduces a real bypass (verified in the plan's execution ledger, Task 5, fix round 1).

- [ ] **Step 4: Run tests to verify they pass**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_db.py" -v
```

Expected: `OK` — all 14 tests pass.

> **Further corrected during Task 9's review**: two more gaps surfaced by cross-checking `db-agent.md`'s prompt claims against this module's actual behavior. (1) `pg_restore` used to match `_SQL_TOOL_RE` and fall through to the generic write-keyword logic, where — since the literal word "pg_restore" isn't itself an INSERT/UPDATE/etc. keyword — it was wrongly treated as read-only and allowed against any host; it's now unified with `pg_dump` under `_PG_BACKUP_TOOL_RE` with the same host gate. (2) The Flyway-allow branch had no check at all beyond the trailing `-Dspring.profiles.active=prod` guard added just above — `_FLYWAY_PROD_DENY_RE` closes the explicit-prod-flag case. Both fixes close the *simple* case; two related gaps remain open and are recorded in Global Constraints and `task-9-fix-report.md` rather than chased further: the `SPRING_PROFILES_ACTIVE=prod` env-var form isn't caught by `_FLYWAY_PROD_DENY_RE` (and a bare `flywayMigrate` with no profile flag is fundamentally unverifiable from text — it depends on whatever `DATABASE_URL` resolves to in the shell), and `_PG_BACKUP_TOOL_RE`'s host check is a whole-string scan that a chained or comment-decoy `localhost` token can defeat.

- [ ] **Step 5: Commit**

Ask for approval first. If approved:

```bash
git add .claude/hooks/policy/db.py .claude/hooks/tests/test_db.py
git commit -m "$(cat <<'EOF'
feat(hooks): add DB command policy module

Text-based check for raw psql/pg_dump/Flyway invocations:
DROP/TRUNCATE/ALTER/GRANT/REVOKE denied everywhere, other writes denied
off localhost:5433, reads allowed anywhere.
EOF
)"
```

---

### Task 6: GitHub policy module

**Files:**
- Create: `.claude/hooks/policy/github.py`
- Create: `.claude/hooks/tests/test_github.py`

**Interfaces:**
- Produces: `github.check(...)`, used by `guard.py` in Task 7.

- [ ] **Step 1: Write the failing tests**

`.claude/hooks/tests/test_github.py`:

```python
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import github


class GithubPolicyTest(unittest.TestCase):
    def test_denies_repo_delete(self):
        result = github.check("Bash", "gh repo delete satyambaldawa/fashion-rental --yes", {})
        self.assertEqual(result[0], "deny")

    def test_denies_pr_merge(self):
        result = github.check("Bash", "gh pr merge 42 --squash", {})
        self.assertEqual(result[0], "deny")

    def test_denies_pr_approve(self):
        result = github.check("Bash", "gh pr review 42 --approve", {})
        self.assertEqual(result[0], "deny")

    def test_denies_cd_workflow_trigger(self):
        result = github.check("Bash", "gh workflow run cd.yml", {})
        self.assertEqual(result[0], "deny")

    def test_denies_infra_provision_trigger(self):
        result = github.check(
            "Bash", "gh workflow run infra-provision.yml -f action=destroy", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_direct_push_to_main(self):
        result = github.check("Bash", "git push origin main", {})
        self.assertEqual(result[0], "deny")

    def test_defers_branch_protection_put_change(self):
        result = github.check(
            "Bash", "gh api -X PUT repos/o/r/branches/main/protection", {}
        )
        self.assertIsNone(result)  # PUT not covered; see next test for DELETE

    def test_denies_branch_protection_delete(self):
        result = github.check(
            "Bash", "gh api -X DELETE repos/o/r/branches/main/protection", {}
        )
        self.assertEqual(result[0], "deny")

    def test_allows_issue_comment(self):
        result = github.check("Bash", 'gh issue comment 7 --body "update"', {})
        self.assertEqual(result[0], "allow")

    def test_allows_ci_workflow_trigger(self):
        result = github.check("Bash", "gh workflow run ci.yml", {})
        self.assertEqual(result[0], "allow")

    def test_allows_pr_view(self):
        result = github.check("Bash", "gh pr view 42", {})
        self.assertEqual(result[0], "allow")

    def test_allows_push_to_feature_branch(self):
        result = github.check("Bash", "git push origin feature/agent-safety-net", {})
        self.assertIsNone(result)

    def test_defers_unrelated_command(self):
        result = github.check("Bash", "pnpm lint", {})
        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
```

Note the `test_defers_branch_protection_put_change` case above is deliberately named to document a known gap: only `-X DELETE`/`--method DELETE` on a branch-protection path is denied; a `PUT`/`PATCH` that weakens protection is not caught by this module (it isn't a `gh` subcommand covered by our deny-list, and it isn't obviously destructive from text alone). It falls through to the normal permission prompt, same as any unmatched command — acceptable per the design's default-ask policy, not a regression.

- [ ] **Step 2: Run tests to verify they fail**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_github.py" -v
```

Expected: `ModuleNotFoundError` or `AttributeError`.

- [ ] **Step 3: Write `.claude/hooks/policy/github.py`**

```python
import re

_DENY_PATTERNS = [
    (re.compile(r"\bgh\s+repo\s+delete\b"), "deletes the GitHub repository"),
    (re.compile(r"\bgh\s+pr\s+merge\b"), "merges are a human, manual action per CLAUDE.md"),
    (
        re.compile(r"\bgh\s+pr\s+review\b[^\n]*--approve\b"),
        "approving a PR is a step toward merging that CLAUDE.md reserves for humans",
    ),
    (
        re.compile(
            r"\bgh\s+workflow\s+run\s+(cd\.yml|infra-provision\.yml|cleanup-gcp-ssh-keys\.yml)\b"
        ),
        "triggers a production-affecting workflow",
    ),
    (
        re.compile(r"\bgh\s+api\b[^\n]*(-X\s*DELETE|--method\s+DELETE)"),
        "issues a DELETE via the GitHub API",
    ),
    (
        re.compile(r"\bgh\s+api\b[^\n]*branches/[^/\s]+/protection[^\n]*(-X\s*DELETE|--method\s+DELETE)"),
        "deletes branch protection",
    ),
    (
        re.compile(r"\bgit\s+push\b[^\n]*\borigin\b[^\n]*\bmain\b"),
        "direct push to main bypasses PR review",
    ),
]

_ALLOW_PATTERNS = [
    re.compile(r"\bgh\s+issue\s+(list|view|create|comment)\b"),
    re.compile(r"\bgh\s+pr\s+(list|view|comment|review)\b"),
    re.compile(r"\bgh\s+run\s+(list|view|watch)\b"),
    re.compile(r"\bgh\s+workflow\s+run\s+ci\.yml\b"),
    re.compile(r"\bgh\s+workflow\s+(list|view)\b"),
]


def check(tool_name, text, tool_input):
    if not text:
        return None
    for pattern, why in _DENY_PATTERNS:
        if pattern.search(text):
            return ("deny", f"Blocked by GitHub policy: this command {why}.")
    for pattern in _ALLOW_PATTERNS:
        if pattern.search(text):
            return ("allow", "Matches the GitHub read/approved-write allow-list.")
    return None
```

Note: the `_DENY_PATTERNS` list has both a generic "any `-X DELETE`" rule and a more specific branch-protection-DELETE rule; the generic one already covers the branch-protection case, so the specific one is redundant today but is kept because it documents intent clearly and won't regress if the generic DELETE rule is ever narrowed. The `test_defers_branch_protection_put_change` test's `-X PUT` case correctly returns `None` (falls through to the deny-pattern loop and doesn't match either DELETE pattern), then falls through the allow-list too (no match), landing on `None` overall — verify this is what Step 4 shows before moving on.

- [ ] **Step 4: Run tests to verify they pass**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_github.py" -v
```

Expected: `OK` — all 13 tests pass.

- [ ] **Step 5: Commit**

Ask for approval first. If approved:

```bash
git add .claude/hooks/policy/github.py .claude/hooks/tests/test_github.py
git commit -m "$(cat <<'EOF'
feat(hooks): add GitHub command policy module

Denies repo delete, PR merge/approve, cd.yml/infra-provision.yml/
cleanup-gcp-ssh-keys.yml triggers, branch-protection DELETE, and direct
push to main; allows issue/PR read+comment and the ci.yml trigger.
EOF
)"
```

---

### Task 7: Guard entrypoint + integration tests

**Files:**
- Create: `.claude/hooks/guard.py`
- Create: `.claude/hooks/tests/test_guard_integration.py`

**Interfaces:**
- Consumes: `secrets.check`, `destructive.check`, `gcp.check`, `db.check`, `github.check` — all `(tool_name: str, text: str, tool_input: dict) -> tuple[str, str] | None`, from Tasks 2–6.
- Produces: `guard.py`, invoked as a subprocess by Claude Code (Task 8) reading a JSON `PreToolUse` payload on stdin and printing `{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": ..., "permissionDecisionReason": ...}}` on stdout, always exiting `0`.

- [ ] **Step 1: Write the failing integration tests**

`.claude/hooks/tests/test_guard_integration.py`:

```python
import json
import os
import subprocess
import sys
import unittest

GUARD_PATH = os.path.join(os.path.dirname(__file__), "..", "guard.py")


def run_guard(tool_name, tool_input, raw_stdin=None):
    stdin_text = (
        raw_stdin
        if raw_stdin is not None
        else json.dumps({"tool_name": tool_name, "tool_input": tool_input})
    )
    result = subprocess.run(
        [sys.executable, GUARD_PATH],
        input=stdin_text,
        capture_output=True,
        text=True,
        timeout=10,
    )
    return result, json.loads(result.stdout)


class GuardIntegrationTest(unittest.TestCase):
    def test_denies_secret_env_read(self):
        _, output = run_guard("Bash", {"command": "echo $JWT_SECRET"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_denies_gcp_instance_delete(self):
        _, output = run_guard(
            "Bash",
            {
                "command": "gcloud compute instances delete fashion-rental-backend "
                "--zone=us-central1-a --quiet"
            },
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_allows_gcp_container_restart(self):
        _, output = run_guard("Bash", {"command": "docker restart fashion-rental-backend"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_denies_db_drop_regardless_of_host(self):
        _, output = run_guard(
            "Bash", {"command": 'psql -h localhost -p 5433 -c "DROP TABLE items"'}
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_allows_db_select_against_any_host(self):
        _, output = run_guard(
            "Bash", {"command": 'psql "$SUPABASE_DATABASE_URL" -c "SELECT 1"'}
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_denies_gh_pr_merge(self):
        _, output = run_guard("Bash", {"command": "gh pr merge 42 --squash"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_allows_gh_ci_trigger(self):
        _, output = run_guard("Bash", {"command": "gh workflow run ci.yml"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_defers_unrelated_command(self):
        _, output = run_guard("Bash", {"command": "pnpm test"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "defer")

    def test_secrets_checked_before_destructive_on_conflicting_command(self):
        # A command that is both a secret leak AND references rm -rf should
        # still report the secrets reason, since secrets.check runs first.
        _, output = run_guard(
            "Bash", {"command": "echo $JWT_SECRET && rm -rf /tmp/whatever"}
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")
        self.assertIn("secrets policy", output["hookSpecificOutput"]["permissionDecisionReason"])

    def test_fails_closed_on_malformed_stdin(self):
        result, output = run_guard(None, None, raw_stdin="not valid json {{{")
        self.assertEqual(result.returncode, 0)
        decision = output["hookSpecificOutput"]["permissionDecision"]
        self.assertIn(decision, ("ask", "deny"))

    def test_fails_closed_on_empty_stdin(self):
        result, output = run_guard(None, None, raw_stdin="")
        self.assertEqual(result.returncode, 0)
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "defer")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_guard_integration.py" -v
```

Expected: failures — `guard.py` doesn't exist yet, so `subprocess.run` will raise `FileNotFoundError` (surfaced as an error, not a clean assertion failure).

- [ ] **Step 3: Write `.claude/hooks/guard.py`**

```python
#!/usr/bin/env python3
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from policy import secrets, destructive, gcp, db, github  # noqa: E402

_CHECKERS = (secrets.check, destructive.check, gcp.check, db.check, github.check)


def _extract_text(tool_input):
    if not isinstance(tool_input, dict):
        return ""
    command = tool_input.get("command")
    return str(command) if command else ""


def decide(tool_name, tool_input):
    text = _extract_text(tool_input)
    for checker in _CHECKERS:
        result = checker(tool_name, text, tool_input or {})
        if result is not None:
            return result
    return ("defer", "")


def main():
    try:
        raw = sys.stdin.read()
        payload = json.loads(raw) if raw.strip() else {}
        tool_name = payload.get("tool_name", "") or ""
        tool_input = payload.get("tool_input", {}) or {}
        decision, reason = decide(tool_name, tool_input)
    except Exception as exc:  # noqa: BLE001 - fail closed on any unexpected error
        decision, reason = (
            "ask",
            f"guard.py could not evaluate this call ({exc}); escalating to manual approval.",
        )

    output = {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": decision,
        }
    }
    if reason:
        output["hookSpecificOutput"]["permissionDecisionReason"] = reason
    print(json.dumps(output))
    sys.exit(0)


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Make it executable**

```bash
chmod +x .claude/hooks/guard.py
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
python3 -m unittest discover -s .claude/hooks/tests -p "test_guard_integration.py" -v
```

Expected: `OK` — all 11 tests pass.

- [ ] **Step 6: Run the full test suite together**

```bash
python3 -m unittest discover -s .claude/hooks/tests -v
```

Expected: `OK` — all tests across every policy module and the integration suite pass (as of Task 9's follow-up fixes: 24 + 11 + 12 + 14 + 13 = 74 from Tasks 2–6, plus 11 from this task = 85; exact count isn't the point — zero failures is).

- [ ] **Step 7: Commit**

Ask for approval first. If approved:

```bash
git add .claude/hooks/guard.py .claude/hooks/tests/test_guard_integration.py
git commit -m "$(cat <<'EOF'
feat(hooks): add guard.py PreToolUse entrypoint

Dispatches Bash tool calls through the five policy modules in order
(secrets, destructive, gcp, db, github) and emits a permissionDecision
of deny/allow/defer. Fails closed (ask) on any unexpected error rather
than allowing.
EOF
)"
```

---

### Task 8: Wire the hook into settings.json + live smoke test

**Files:**
- Create or modify: `.claude/settings.json` (gitignored in this repo — see Global Constraints; this task has no commit step)

**Interfaces:**
- Consumes: `.claude/hooks/guard.py` from Task 7 (must already be executable).

- [ ] **Step 1: Check current contents of `.claude/settings.json`**

```bash
cat .claude/settings.json 2>/dev/null || echo "(does not exist)"
```

If it already contains unrelated settings, merge the `hooks` block below into the existing JSON rather than overwriting it. If it doesn't exist or is empty, create it fresh with exactly this content:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/guard.py"
          }
        ]
      }
    ]
  }
}
```

- [ ] **Step 2: Restart Claude Code (or start a fresh session) so the hook registration is picked up**

Hooks are read from settings at session start. Tell the user to restart their Claude Code session before the live smoke test in the next step.

- [ ] **Step 3: Live smoke test — one denied command per domain**

In the restarted session, attempt each of these and confirm the tool call is blocked with a visible reason citing the matching policy (not a silent failure, not a generic permission-denied):

```
printenv JWT_SECRET
```
Expected: blocked, reason mentions secrets policy.

```
gcloud compute instances delete fashion-rental-backend --zone=us-central1-a --quiet
```
Expected: blocked, reason mentions GCP policy.

```
gh pr merge 1 --squash
```
Expected: blocked, reason mentions GitHub policy / CLAUDE.md.

```
psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c "DROP TABLE items"
```
Expected: blocked, reason mentions DROP/TRUNCATE/ALTER/GRANT/REVOKE are never permitted. Confirm the table still exists afterward (`\d items` via the same `psql` connection).

- [ ] **Step 4: Live smoke test — one allowed command per domain, confirm no spurious block**

```
docker ps
gh pr list
psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c "SELECT 1"
```

Expected: each runs without an unexpected block. (Whether it still needs an interactive approval or runs silently depends on your Claude Code permission mode outside this hook's scope — the important thing is the hook itself doesn't deny it.)

This task has no commit step: `.claude/settings.json` is gitignored (see Global Constraints), so this change only ever exists on the local machine.

---

### Task 9: The three ops subagents

**Files:**
- Create: `.claude/agents/gcp-agent.md`
- Create: `.claude/agents/db-agent.md`
- Create: `.claude/agents/github-agent.md`

**Interfaces:**
- Consumes: the guard hook from Tasks 7–8 (referenced in each agent's "what you must not do" section as the actual enforcement mechanism, not just a prompt-level instruction).

- [ ] **Step 1: Write `.claude/agents/gcp-agent.md`**

```markdown
---
name: "gcp-agent"
description: "Ops diagnostics for the GCP-hosted backend VM: instance status, container logs, network/firewall checks, billing/cost checks, and restarting the backend container. Read-mostly; all other GCP mutations are blocked by the .claude/hooks/guard.py PreToolUse hook regardless of what this agent attempts."
tools: Bash, Read, Grep
model: sonnet
color: blue
---

You are the **GCP Ops Agent** for the fashion rental application's production backend, a single
`e2-micro` VM (`fashion-rental-backend`, zone `us-central1-a`) running the Spring Boot backend in
Docker, provisioned via `.github/workflows/infra-provision.yml` (see `infra/deployment-plan.md`
for the full architecture).

## What you do

- Check VM/instance status: `gcloud compute instances describe fashion-rental-backend --zone=us-central1-a`
- Tail or fetch container logs: `gcloud compute ssh fashion-rental-backend --zone=us-central1-a --command="docker logs --tail 200 fashion-rental-backend"`
- Check the container is up: `docker ps`
- Check network/firewall configuration: `gcloud compute firewall-rules list`, `gcloud compute networks describe`
- Check billing/cost: `gcloud billing accounts list`, budget/usage queries
- Restart a hung backend container: `gcloud compute ssh fashion-rental-backend --zone=us-central1-a --command="docker restart fashion-rental-backend"` — this is the **only** write action you may take.

## What you must NOT do

You must never attempt to delete, stop, or recreate the VM, its static IP, firewall rules, disks,
or IAM identities; delete or recreate Docker containers/volumes other than the one restart above;
read secrets (`gcloud secrets ...`, `docker inspect`, `.env*` files, or any `*_PASSWORD`/`*_SECRET`/
`*_KEY`/`*_TOKEN`-named variable); or run any destructive shell command. These are enforced by a
repo-wide hook (`.claude/hooks/guard.py`) that will block the attempt regardless — but do not try,
and do not suggest the user bypass it. If a diagnosis requires an action outside this list, report
what you found and what action you believe is needed; let the user (or CLAUDE.md's git/infra rules)
decide, and do it themselves.

## How you report

State what you checked, what you found, and — if something is wrong — your diagnosis and the
minimal next step. Do not speculate about causes you haven't checked for.
```

- [ ] **Step 2: Write `.claude/agents/db-agent.md`**

```markdown
---
name: "db-agent"
description: "Database inspection and local-dev migrations for the fashion rental Postgres database via psql/pg_dump/Flyway. Read-only SELECTs are permitted against local dev or prod Supabase; writes are permitted only against local dev via Flyway. All of this is enforced by the .claude/hooks/guard.py PreToolUse hook regardless of what this agent attempts."
tools: Bash, Read
model: sonnet
color: purple
---

You are the **Database Ops Agent** for the fashion rental application. The schema and domain
rules live in `technical-architecture.md` and `fashion-rental-discovery.md` — read them before
answering non-trivial questions about what the data means. There is no MCP tool for the
database; you talk to Postgres directly via `psql`/`pg_dump`/Flyway through `Bash`.

## What you do

- Inspect schema: `psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c '\dt'` /
  `-c '\d <table>'`, or query `information_schema` directly, against local dev
  (credentials from `docker-compose.yml`)
- Run read-only `SELECT`/`EXPLAIN`/`SHOW` queries via `psql` — these work against **whichever
  database `FASHION_RENTAL_DB_URL` currently points to** (local dev or prod Supabase); any
  write keyword in the same command is blocked by the guard hook regardless of host.
- Run local-dev Flyway migrations: `cd backend && ./gradlew flywayMigrate` — only ever against
  `localhost:5433`. Read the migration file(s) in `db/migration/` first and confirm they're
  additive/reviewed before running.
- Run local-dev `pg_dump` for inspection/backup testing against `localhost:5433`.

## What you must NOT do

You must never run `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, or `REVOKE` on any database, on any
host. You must never run `INSERT`/`UPDATE`/`DELETE`/DDL against anything other than
`localhost:5433` — production writes go through the application's own transactional code paths
and reviewed migrations merged via PR, never ad hoc. You must never run `pg_dump`/`pg_restore`
against the production Supabase host — that's `db-backup.yml`'s job. You must never read
`SUPABASE_DATABASE_PASSWORD`, `SUPABASE_DATABASE_URL`, `NEON_PG_URL`, or any other credential
value. These are enforced by `.claude/hooks/guard.py` regardless — don't attempt them, and don't
suggest a bypass.

## How you report

State the query you ran, against which source, and the result. For schema questions, cite the
actual columns/constraints you found — don't guess from memory of the entity classes.
```

- [ ] **Step 3: Write `.claude/agents/github-agent.md`**

```markdown
---
name: "github-agent"
description: "Read access to GitHub issues, PRs, and workflow runs for this repo, plus opening/commenting/labeling issues and PRs and triggering the ci.yml workflow. Cannot merge PRs, delete the repo, or trigger cd.yml/infra-provision.yml/cleanup-gcp-ssh-keys.yml — blocked by the .claude/hooks/guard.py PreToolUse hook regardless of what this agent attempts."
tools: Bash, Read, WebFetch
model: sonnet
color: gray
---

You are the **GitHub Ops Agent** for this repository. You answer questions about issues, pull
requests, and CI status, and can take a narrow set of non-destructive actions using the `gh` CLI.

## What you do

- Read: `gh issue list|view`, `gh pr list|view|diff`, `gh run list|view|watch`,
  `gh workflow list|view`
- Open, comment on, or label issues and PRs: `gh issue create|comment`, `gh pr comment`,
  `gh pr review` (comment only — never `--approve` or used to gate a merge)
- Re-run CI on demand: `gh workflow run ci.yml`

## What you must NOT do

You must never merge a PR (`gh pr merge`), delete the repository, approve a PR, trigger
`cd.yml`, `infra-provision.yml`, or `cleanup-gcp-ssh-keys.yml` (these touch production and stay
manual per CLAUDE.md), push directly to `main`, modify branch protection, or read/write repo
secrets (`gh secret ...`). These are enforced by `.claude/hooks/guard.py` regardless — don't
attempt them, and don't suggest the user bypass it. Per CLAUDE.md, all commits/pushes/PR
creation from the main session still require the user's explicit approval — you have no
authority to override that.

## How you report

Summarize what you found (issue/PR state, CI status) plainly. When you comment on something,
quote back exactly what you posted so the user can verify it before it's easy to lose track of.
```

- [ ] **Step 4: Commit**

Ask for approval first. If approved:

```bash
git add .claude/agents/gcp-agent.md .claude/agents/db-agent.md .claude/agents/github-agent.md
git commit -m "$(cat <<'EOF'
feat(agents): add gcp-agent, db-agent, github-agent

Read-mostly ops subagents scoped to the actions approved in
docs/superpowers/specs/2026-08-07-agent-safety-net-design.md. Actual
enforcement is the guard.py hook (Tasks 2-8), not these prompts — the
prompts exist so each agent understands and reports its own boundaries.
EOF
)"
```

---

## Self-Review Notes

- **Spec coverage:** every bullet in the design doc's Section 2/3 (agents table, decision order, secrets/destructive/gcp/db/github rules) maps to a task above. The one deviation from the original design doc — dropping the `dbhub` MCP server entirely in favor of direct `psql`/`pg_dump`/Flyway access — was confirmed with the user during planning (after discovering the pinned `dbhub` version's `--readonly` flag was deprecated, which prompted reconsidering whether the MCP server was worth keeping at all) and is a simplification, not a reduction in what's guarded: the DB agent still can't write outside `localhost:5433`, still can't `DROP`/`TRUNCATE`/etc. anywhere, still can read anywhere — enforced by the same `db.py` module either way.
- **Type/signature consistency:** all five policy modules share the exact signature `check(tool_name: str, text: str, tool_input: dict) -> tuple[str, str] | None`, verified against how `guard.py` (Task 7) calls them.
- **No placeholders:** every regex, every test assertion, every command in every step is real and was checked against this repo's own files (`infra/deployment-plan.md`, `.github/workflows/*.yml`, `.gitignore`).
