# Agent Safety Net: Scoped Ops Subagents + Command Guardrail Hook

**Status**: Approved (design phase)
**Date**: 2026-08-07

## Problem

The user wants three new subagents to handle day-to-day operational questions without
manually running `gcloud`/`psql`/`gh` themselves each time:

1. A **GCP agent** — VM status, logs, network, cost.
2. A **database agent** — schema inspection, queries, local migrations.
3. A **GitHub agent** — issues, PRs, CI status.

Because these agents run real CLI tools against real infrastructure (a production GCP VM,
a production Supabase database, the GitHub repo), a mistake or a bad instruction has real
blast radius: dropping a table, force-pushing over history, deleting the VM, or leaking a
credential into a transcript or a git commit. The ask is for a **hard technical guardrail**
around what these tools (and, since the enforcement is global, any Claude Code session in
this repo) can execute — not just written policy in an agent prompt, which an agent can be
talked out of. Prompts are advisory; hooks are enforced by the harness before the tool ever
runs.

## Non-goals

- No changes to existing GitHub Actions workflows (`ci.yml`, `cd.yml`, `infra-provision.yml`,
  `db-backup.yml`, `cleanup-gcp-ssh-keys.yml`) — those already encode the "manual trigger for
  anything destructive" policy at the CI/CD layer.
- No Terraform / IaC introduction — GCP provisioning stays `gcloud`-via-workflow as today.
- No agent gets the ability to deploy, provision infra, merge PRs, or write to production. That
  stays a human, manual action, per CLAUDE.md's existing git rules.

## Architecture

### 1. Three new subagents (`.claude/agents/*.md`)

Follow the existing convention set by `business-lead.md` / `tech-lead.md` / `devils-advocate.md`:
YAML frontmatter (`name`, `description`, `tools`, `model`, `color`), a role prompt, and an
explicit "what you must NOT do" section reinforcing (not replacing) the hook-level restriction.

| Agent | File | Purpose | Tools |
|---|---|---|---|
| `gcp-agent` | `.claude/agents/gcp-agent.md` | Instance status, `docker logs`, network/firewall reads, billing/cost checks, restart the backend container | `Bash`, `Read`, `Grep` |
| `db-agent` | `.claude/agents/db-agent.md` | Schema inspection, `SELECT` queries (local dev + read-only against prod Supabase), Flyway migrations (local dev only) | `Bash`, `Read`, `mcp__postgres__*` |
| `github-agent` | `.claude/agents/github-agent.md` | Read issues/PRs/workflow runs; open/comment/label issues & PRs; trigger `ci.yml` only | `Bash`, `Read`, `WebFetch` |

None of the three can edit files, commit, push, deploy, or provision. They are diagnostic/ops
assistants, not builders.

### 2. Global `PreToolUse` guardrail hook

A single hook, registered in `.claude/settings.json`, matches every `Bash` call and every call
to the `postgres` MCP tool — **for every session, main or subagent** (the user's explicit
choice: this is a repo-wide rule, not an agent-specific leash).

```
.claude/
  hooks/
    guard.py                 # PreToolUse entrypoint: reads stdin JSON, dispatches, emits decision
    policy/
      secrets.py              # credential/env-leak patterns — checked first, no exceptions
      destructive.py          # cross-cutting irreversible ops (rm -rf, force-push, reset --hard, ...)
      gcp.py                   # gcloud / docker(-via-ssh) allow+deny patterns
      db.py                    # psql / pg_dump / flyway allow+deny patterns + host check
      github.py                # gh CLI allow+deny patterns
    tests/
      test_secrets.py
      test_destructive.py
      test_gcp.py
      test_db.py
      test_github.py
      test_guard_integration.py   # feeds synthetic PreToolUse payloads through guard.py
```

**Decision order** for every intercepted call:

1. `secrets.py` — hard-deny credential access. Runs unconditionally, first, regardless of
   which agent (or the main session) issued the call.
2. `destructive.py` — hard-deny cross-cutting irreversible operations.
3. Domain deny-list (`gcp.py` / `db.py` / `github.py`, matched by command shape, not by which
   agent is running) — hard-deny known-destructive-for-that-domain commands.
4. Domain allow-list — auto-allow known-safe reads and the specific approved writes (container
   restart, local Flyway migrate, `gh workflow run ci.yml`, issue/PR comments). Skips the
   permission prompt for routine ops.
5. Anything unmatched falls through to Claude Code's normal interactive permission prompt
   (default-ask) — nothing is silently allowed, nothing outside the above is silently blocked.

A blocked call returns a specific reason (which rule fired and why) so it's visible in the
transcript, not just a bare failure.

**Fail-closed requirement**: if `guard.py` itself errors (bad stdin, unexpected exception), it
must produce a deny/ask decision, never fall open to allow. A crashing safety hook must not
become a bypass.

The exact stdin/stdout JSON contract for `PreToolUse` hooks will be confirmed against current
Claude Code documentation at implementation time rather than assumed here; the architecture
above (dispatch → ordered policy checks → decision) doesn't depend on the exact schema.

### 3. Command policy (exact rules)

**Secrets (`secrets.py`)** — checked first, everywhere:
- Deny bare `env`, `printenv` (no args), `export` (no args) — full environment dumps.
- Deny `cat`/`less`/`head`/`tail`/`grep`/any editor targeting `.env*`, `*key.json`,
  `*service-account*`.
- Deny any reference — in `echo`, `printenv <name>`, or interpolated into another command
  (e.g. piped into `curl`) — to a variable name matching
  `(PASSWORD|SECRET|TOKEN|_KEY|KEY_ID|DSN|DATABASE_URL)` case-insensitive.
- Deny `gcloud secrets versions access|describe`, `gh secret list|get|set|delete`,
  `docker inspect`, `docker exec ... env`, `cat /proc/*/environ` (the repo's own
  `infra/deployment-plan.md` accepted-risks table notes `docker inspect` leaks env vars —
  this closes that gap for agent use).
- Non-sensitive named vars (`VITE_API_URL`, `SPRING_PROFILES_ACTIVE`, etc.) are unaffected.

**Cross-cutting destructive (`destructive.py`)** — deny always: `rm -rf`, `git push --force`/
`-f`, `git reset --hard`, `git clean -f`/`-fd`, `git branch -D`, `git filter-branch`, `dd if=`,
`mkfs`, `shutdown`, `reboot`, `kill -9 1`, `chmod -R 777`.

**GCP (`gcp.py`)**:
- Deny: `instances delete`, `instances stop`, `addresses delete`, `firewall-rules delete`,
  `disks delete`, `projects delete`, `iam service-accounts delete|keys delete`, `docker rm`,
  `docker stop`, `docker system prune -a --volumes`, `docker volume rm`.
- Allow: any `describe`/`list`/`get-*` verb, `docker logs`, `docker ps`,
  `docker restart fashion-rental-backend` (the one approved write), billing/quota reads.

**DB (`db.py`)**:
- Deny always, any host: `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `REVOKE`.
- Deny `INSERT`/`UPDATE`/`DELETE`/DDL whenever the connection string's host is not
  `localhost`/`127.0.0.1:5433`.
- The `postgres` MCP tool (`dbhub`) is **SELECT/EXPLAIN/SHOW-only, always** — its target host
  isn't inspectable per call (fixed at server startup via `FASHION_RENTAL_DB_URL`), so writes
  through it are never allowed regardless of what that DSN currently points to.
- Allow: `SELECT`/`EXPLAIN`/schema introspection anywhere (local or prod, read-only only);
  `./gradlew flywayMigrate` and `pg_dump` only when the target is `localhost:5433`.

**GitHub (`github.py`)**:
- Deny: `gh repo delete`, `gh pr merge`, `gh workflow run cd.yml|infra-provision.yml|
  cleanup-gcp-ssh-keys.yml`, `gh api` calls using `DELETE` or hitting branch-protection paths,
  direct `git push` to `main`.
- Allow: `gh issue list|view|create|comment`, `gh pr list|view|comment|review` (comment only,
  never approve/merge), `gh run list|view|watch`, `gh workflow run ci.yml`,
  `gh workflow list|view`.

Anything not covered by an allow or deny pattern above falls through to the normal permission
prompt.

## Testing & validation plan

1. **Policy unit tests** (pure Python, no harness needed): for every deny pattern, assert it
   matches the literal destructive commands already present in this repo's own workflows
   (e.g. the `gcloud compute instances delete` line in `infra-provision.yml`'s destroy path);
   for every allow pattern, assert it matches real safe commands from the same files; assert a
   battery of near-miss commands do *not* trip an unrelated deny rule (e.g. `instances
   describe` must not match the `instances delete` pattern).
2. **Hook integration test**: feed synthetic `PreToolUse` JSON payloads through `guard.py` via
   stdin for a table of ~30 representative commands spanning all three domains plus the
   secrets/destructive cases; assert the resulting decision.
3. **Manual smoke test**: after wiring into `settings.json`, attempt one denied command per
   domain live (e.g. `printenv JWT_SECRET`, `gh pr merge`, a `DROP TABLE` via the postgres MCP
   tool) and confirm each is blocked with a clear reason; attempt one allowed command per
   domain and confirm it is not spuriously blocked.
4. **Fail-safe check**: force `guard.py` to raise (malformed stdin) and confirm the resulting
   decision is deny/ask, never allow.

## Open items for implementation

- Confirm the exact current `PreToolUse` hook stdin/stdout JSON contract against Claude Code
  docs before writing `guard.py`.
- Confirm whether the `postgres` MCP tool's parameter name for the SQL text is `query`, `sql`,
  or something else, so `db.py` can inspect the right field.
