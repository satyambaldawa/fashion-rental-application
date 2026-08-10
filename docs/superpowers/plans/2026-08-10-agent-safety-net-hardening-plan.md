# Agent Safety Net — Skills-First Hardening Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement **Phases 0–1** task-by-task. Steps use checkbox (`- [ ]`) syntax. **Phase 2 is a human-executed ops runbook; Phase 3 is a future-architecture design gate** — do not auto-execute them.

**Goal:** Replace the three ops *subagents* with three *skills*, keep every enforcement control in the agent-agnostic layers (the `guard.py` hook + scoped credentials), and shed the funnel and handoff machinery that only existed to serve subagent identity.

**Architecture (the pivot):** A skill loads operational know-how into the *current* context; it does not create a separate identity or an enforced toolset. Empirically verified this session: the PreToolUse hook sees `agent_type: <ABSENT>` for the main session and only a real subagent populates it. Therefore **skills cannot be a security control** — they are guidance. Security lives entirely in: (1) `.claude/settings.json` `permissions.deny` for the file-reading tools, (2) the `guard.py` policies that fire on Bash command *text* regardless of caller, and (3) scoped credentials. The three domain policies (`gcp`/`db`/`github`) stay; the funnel (`agent_scope`) is removed because it has nothing to anchor on in a skills world.

**Tech Stack:** Python 3 stdlib + `unittest` (hook policies); Claude Code `PreToolUse` hooks, `permissions` block, and `.claude/skills/*/SKILL.md`; PostgreSQL roles; GCP IAM; GitHub fine-grained tokens.

## Why this supersedes the earlier agent-based draft

The prior draft of this plan hardened a subagent + funnel design. The skills decision makes two of its phases unnecessary:
- **Domain registry (dropped):** it existed to de-duplicate the push-to-main patterns shared between `agent_scope.py` and `github.py`. With `agent_scope.py` gone, only one copy remains (in `github.py`) — no duplication to fix.
- **Handoff contract (dropped):** it existed because subagents can't self-dispatch and had to "report up by name." One context invoking skills in sequence has no cross-agent routing at all.

Net effect: the plan is materially smaller. That is the point of the skills route.

## Global Constraints

- **Python stdlib only**, tests via `unittest`. No `pip install`.
- **guard.py always fails toward `ask`/`deny`, never open.** Any exception → `ask`.
- **Skills are guidance, never a control.** Every skill body must state that enforcement is the hook + credentials, and must not imply the skill itself prevents anything.
- **`agent_type` is ABSENT for the main session** (verified). No code may depend on it for a security decision. (After Phase 0 the policy `check` signature is back to `check(tool_name, text, tool_input)` — no `agent_type` parameter.)
- **No secrets in committed files.** Public repo. Runbook artifacts (Phase 2) use `<placeholder>` values only.
- **`.claude/settings.json` is tracked**; `.claude/settings.local.json` stays gitignored.
- **Always ask before git commit/push/PR** (CLAUDE.md). Every task's final step proposes a commit and waits for approval. Phase 0 discards uncommitted exploratory code — get explicit approval before running it.
- **Text-matching has a known ceiling.** No regex closes script/SDK indirection. Phase 2 (credentials) is what actually closes it; the hook is defense-in-depth and immediate feedback.
- **Escape hatch (documented, not built):** if a future read path processes *untrusted external responses* (a crafted DB row, a poisoned log line, a hostile issue body) and blast-radius containment matters, a subagent may be reintroduced *for that path only* — for the isolation, not for identity. Default remains skills.

---

## File Structure

**Phase 0 — Reset to the enforcement-only base:**
- Discard (uncommitted, exploratory): `.claude/hooks/policy/agent_scope.py`, `.claude/hooks/tests/test_agent_scope.py`, and the uncommitted `agent_type` threading in `guard.py` + the five policy files + `test_guard_integration.py`.
- Keep + commit: the `permissions.deny` block already added to `.claude/settings.json`.

**Phase 1 — Agents → skills:**
- Create: `.claude/skills/db-ops/SKILL.md`, `.claude/skills/gcp-ops/SKILL.md`, `.claude/skills/github-ops/SKILL.md`.
- Delete: `.claude/agents/db-agent.md`, `.claude/agents/gcp-agent.md`, `.claude/agents/github-agent.md`.
- Create: `.claude/hooks/README.md` (trust model, skills-first).
- Modify: `.claude/hooks/tests/test_guard_integration.py` (add explicit "main session is enforced" assertions).
- Modify: `CLAUDE.md` (short subsection: skills + hook + credentials model).

**Phase 2 — Scoped credentials (ops runbook, human-executed):**
- Create: `infra/agent-credentials-runbook.md`.

**Phase 3 — Capability-scoped tools (future gate):**
- This document only.

---

## Phase 0 — Reset to the enforcement-only base

**Why first:** the skills route keeps the hook policies but drops the funnel. Get back to a clean base — five policies (`secrets`, `destructive`, `gcp`, `db`, `github`), no `agent_scope`, no `agent_type` threading — plus the good `.env` deny rules, before adding skills.

### Task 0: Discard the funnel, keep the env-deny, commit the base

**Files:**
- Restore to last commit (discard uncommitted mods): `.claude/hooks/guard.py`, `.claude/hooks/policy/{secrets,destructive,gcp,db,github}.py`, `.claude/hooks/tests/test_guard_integration.py`
- Delete (untracked): `.claude/hooks/policy/agent_scope.py`, `.claude/hooks/tests/test_agent_scope.py`
- Keep modified (do NOT restore): `.claude/settings.json`

- [ ] **Step 1: Confirm what is uncommitted** (so the discard is deliberate)

Run: `git status --short`
Expected: modified `guard.py`, the 5 policies, `test_guard_integration.py`, `settings.json`; untracked `agent_scope.py`, `test_agent_scope.py`.

- [ ] **Step 2: Discard the funnel work** (get explicit approval first — this throws away uncommitted code)

```bash
git restore .claude/hooks/guard.py \
  .claude/hooks/policy/secrets.py .claude/hooks/policy/destructive.py \
  .claude/hooks/policy/gcp.py .claude/hooks/policy/db.py .claude/hooks/policy/github.py \
  .claude/hooks/tests/test_guard_integration.py
rm .claude/hooks/policy/agent_scope.py .claude/hooks/tests/test_agent_scope.py
```

- [ ] **Step 3: Verify the base is intact and green**

Run: `python3 -m unittest discover -s .claude/hooks/tests -v`
Expected: PASS at the pre-funnel count (the five-policy suite; `test_agent_scope` and the funnel integration tests are gone). Confirm `guard.py` imports only `secrets, destructive, gcp, db, github` and `check(...)` takes three args.

- [ ] **Step 4: Confirm settings.json still carries the env-deny block**

Run: `grep -c "Read(\*\*/.env)" .claude/settings.json`
Expected: `1` (the permissions block was NOT restored).

- [ ] **Step 5: Commit the env-deny base** (propose message, wait for approval)

```bash
git add .claude/settings.json
git commit -m "feat(hooks): deny Read/Edit/Grep on .env* and key/service-account files"
```

---

## Phase 1 — Agents become skills

**Why:** the operational know-how (how to connect to Postgres, which gcloud reads are useful, the reporting format) was always *instructional*. Skills are the right home for it — lighter, context-aware, composable — while the hook keeps enforcing regardless of who runs the command.

### Task 1: Create the `db-ops` skill

**Files:**
- Create: `.claude/skills/db-ops/SKILL.md`

- [ ] **Step 1: Write `.claude/skills/db-ops/SKILL.md`**

```markdown
---
name: db-ops
description: Use when inspecting the fashion-rental Postgres schema, running read-only SELECT/EXPLAIN queries against local dev or prod, or running local-dev Flyway migrations. Covers how to connect and what is safe. Enforcement is the guard hook + scoped credentials, not this skill.
---

# Database ops

Operational know-how for talking to the fashion-rental Postgres database directly
via `psql` / `pg_dump` / Flyway. Schema and domain rules live in
`technical-architecture.md` and `fashion-rental-discovery.md` — read them before
answering non-trivial questions about what the data means.

**This skill is guidance, not a wall.** What is actually permitted is enforced by
`.claude/hooks/guard.py` (which blocks writes/DDL against non-local hosts, all
`DROP`/`TRUNCATE`/`ALTER`/`GRANT`/`REVOKE`, and secret reads on any host) and by the
scoped database role the session runs as. Do not attempt to bypass either, and do
not suggest the user bypass them.

## What you do

- Inspect schema against local dev: `psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c '\dt'` / `-c '\d <table>'`.
- Run read-only `SELECT`/`EXPLAIN`/`SHOW` — these work against whichever database the connection URL points to (local dev or prod). Any write keyword in the same command is blocked by the hook regardless of host.
- Run local-dev Flyway migrations: `cd backend && ./gradlew flywayMigrate` — only ever against `localhost:5433`. Read the migration file(s) in `db/migration/` first and confirm they are additive/reviewed.
- Run local-dev `pg_dump` for inspection against `localhost:5433`.

## What is off-limits

`DROP`/`TRUNCATE`/`ALTER`/`GRANT`/`REVOKE` on any host; `INSERT`/`UPDATE`/`DELETE`/DDL
against anything other than `localhost:5433`; `pg_dump`/`pg_restore` against prod
(that is `db-backup.yml`'s job); reading any credential value. The hook enforces all
of this — treat it as the boundary, and report rather than work around it.

## How you report

State the query you ran, against which source, and the result. For schema questions,
cite the actual columns/constraints you found — do not guess from the entity classes.
```

- [ ] **Step 2: Verify the skill is discoverable and enforcement is unchanged**

Manual check: confirm `.claude/skills/db-ops/SKILL.md` has valid frontmatter (`name`, `description`). Then run a hard-enforcement probe from the main session (no subagent): `psql -h prod-db -c "DROP TABLE items"` must be denied by the hook. This proves the skill did not weaken enforcement — the hook still blocks regardless of context.

- [ ] **Step 3: Commit** (propose message, wait for approval)

```bash
git add .claude/skills/db-ops/SKILL.md
git commit -m "feat(skills): add db-ops skill (operational know-how; hook enforces)"
```

### Task 2: Create the `gcp-ops` skill

**Files:**
- Create: `.claude/skills/gcp-ops/SKILL.md`

- [ ] **Step 1: Write `.claude/skills/gcp-ops/SKILL.md`**

```markdown
---
name: gcp-ops
description: Use when diagnosing the GCP-hosted backend VM — instance status, container logs, network/firewall checks, billing/cost, or restarting the backend container. Covers how to run these read-mostly ops safely. Enforcement is the guard hook + scoped credentials, not this skill.
---

# GCP ops

Operational know-how for the production backend: a single `e2-micro` VM
(`fashion-rental-backend`, zone `us-central1-a`) running the Spring Boot backend in
Docker, provisioned via `.github/workflows/infra-provision.yml` (see
`infra/deployment-plan.md`).

**This skill is guidance, not a wall.** What is actually permitted is enforced by
`.claude/hooks/guard.py` (which blocks VM/IP/firewall/disk/IAM delete and create,
container removal, and secret reads) and by the viewer-scoped service account the
session runs as. Do not attempt to bypass either.

## What you do

- VM status: `gcloud compute instances describe fashion-rental-backend --zone=us-central1-a`.
- Container logs: `gcloud compute ssh fashion-rental-backend --zone=us-central1-a --command="docker logs --tail 200 fashion-rental-backend"`.
- Container up? `docker ps`. Network/firewall: `gcloud compute firewall-rules list`, `gcloud compute networks describe`. Cost: `gcloud billing accounts list`.
- Restart a hung backend container: `gcloud compute ssh ... --command="docker restart fashion-rental-backend"` — the only write action.

## What is off-limits

Deleting/stopping/recreating the VM, static IP, firewall rules, disks, or IAM
identities; removing containers/volumes other than the one restart; reading secrets
(`gcloud secrets`, `docker inspect`, `.env*`, `*_PASSWORD`/`*_SECRET`/`*_KEY`/`*_TOKEN`).
The hook enforces all of this — report the needed action, let the user do it.

## How you report

State what you checked, what you found, and — if something is wrong — your diagnosis
and the minimal next step. Do not speculate about causes you have not checked.
```

- [ ] **Step 2: Verify enforcement unchanged**

From the main session, `gcloud compute instances delete fashion-rental-backend --zone=us-central1-a --quiet` must be denied by the hook.

- [ ] **Step 3: Commit** (propose message, wait for approval)

```bash
git add .claude/skills/gcp-ops/SKILL.md
git commit -m "feat(skills): add gcp-ops skill (operational know-how; hook enforces)"
```

### Task 3: Create the `github-ops` skill

**Files:**
- Create: `.claude/skills/github-ops/SKILL.md`

- [ ] **Step 1: Write `.claude/skills/github-ops/SKILL.md`**

```markdown
---
name: github-ops
description: Use when reading GitHub issues, PRs, or CI runs for this repo, opening/commenting/labeling issues and PRs, or triggering the ci.yml workflow. Covers the safe gh commands. Enforcement is the guard hook + a fine-grained token, not this skill.
---

# GitHub ops

Operational know-how for issues, pull requests, and CI via the `gh` CLI.

**This skill is guidance, not a wall.** What is actually permitted is enforced by
`.claude/hooks/guard.py` (which blocks `gh pr merge`, repo delete, PR approve,
production workflow triggers, direct push to `main`, branch-protection edits, and
secret reads) and by the fine-grained token the session holds. Do not attempt to
bypass either. Per CLAUDE.md, all commits/pushes/PR creation still require the user's
explicit approval.

## What you do

- Read: `gh issue list|view`, `gh pr list|view|diff`, `gh run list|view|watch`, `gh workflow list|view`.
- Open/comment/label issues and PRs: `gh issue create|comment`, `gh pr comment`, `gh pr review` (comment only — never `--approve`).
- Re-run CI: `gh workflow run ci.yml`.

## What is off-limits

`gh pr merge`, deleting the repo, approving a PR, triggering `cd.yml` /
`infra-provision.yml` / `cleanup-gcp-ssh-keys.yml`, pushing directly to `main`,
editing branch protection, reading/writing repo secrets. The hook enforces all of
this — report and let the user act.

## How you report

Summarize issue/PR state and CI status plainly. When you comment on something, quote
back exactly what you posted so the user can verify it.
```

- [ ] **Step 2: Verify enforcement unchanged**

From the main session, `gh pr merge 82 --squash` must be denied by the hook; `gh pr view 82` must be allowed.

- [ ] **Step 3: Commit** (propose message, wait for approval)

```bash
git add .claude/skills/github-ops/SKILL.md
git commit -m "feat(skills): add github-ops skill (operational know-how; hook enforces)"
```

### Task 4: Delete the three ops agents

**Files:**
- Delete: `.claude/agents/db-agent.md`, `.claude/agents/gcp-agent.md`, `.claude/agents/github-agent.md`

- [ ] **Step 1: Remove the agent definitions**

```bash
git rm .claude/agents/db-agent.md .claude/agents/gcp-agent.md .claude/agents/github-agent.md
```

- [ ] **Step 2: Confirm nothing references them**

Run: `grep -rn "db-agent\|gcp-agent\|github-agent" .claude CLAUDE.md docs || echo "no references"`
Expected: only historical references in `docs/` plans/specs (acceptable); no live config pointing at the deleted agents. Fix any live reference found.

- [ ] **Step 3: Commit** (propose message, wait for approval)

```bash
git commit -m "refactor: remove ops subagents in favor of skills + hook enforcement"
```

### Task 5: Trust-model README, enforcement tests, CLAUDE.md

**Files:**
- Create: `.claude/hooks/README.md`
- Modify: `.claude/hooks/tests/test_guard_integration.py`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: `guard.py`'s `decide(tool_name, tool_input) -> (decision, reason)` (three-arg, post-Phase-0).

- [ ] **Step 1: Write the failing enforcement test**

Add to `.claude/hooks/tests/test_guard_integration.py` — assert the main session (no `agent_type`) is fully enforced now that there is no funnel:

```python
def test_main_session_is_enforced_without_a_funnel(self):
    # Dangerous commands must be denied for the plain main session; safe ones allowed.
    deny_cases = [
        "echo $JWT_SECRET",
        "rm -rf frontend/node_modules",
        'psql -h prod-db -c "DROP TABLE items"',
        "gh pr merge 82 --squash",
        "gcloud compute instances delete fashion-rental-backend --zone=us-central1-a --quiet",
    ]
    for cmd in deny_cases:
        _, output = run_guard("Bash", {"command": cmd})
        self.assertEqual(
            output["hookSpecificOutput"]["permissionDecision"], "deny", cmd
        )
    allow_cases = ["gh pr view 82", 'psql -h localhost -p 5433 -c "SELECT 1"']
    for cmd in allow_cases:
        _, output = run_guard("Bash", {"command": cmd})
        self.assertEqual(
            output["hookSpecificOutput"]["permissionDecision"], "allow", cmd
        )
```

- [ ] **Step 2: Run it — it should PASS immediately**

Run: `cd .claude/hooks && python3 -m unittest tests.test_guard_integration -v`
Expected: PASS. (This test documents intended behavior; the hook already enforces it. If any case fails, that is a real regression to fix before proceeding.)

- [ ] **Step 3: Create `.claude/hooks/README.md`**

```markdown
# Agent safety net — how it works and what it does NOT guarantee

Three enforcement layers, all agent-agnostic (they do not care whether a skill,
a subagent, or the main session issued the command):

1. **`permissions.deny` in `.claude/settings.json`** — blocks the Read/Edit/Grep
   tools from touching `.env*`, `*key.json`, `*service-account*`. Highest
   precedence, evaluated before hooks. The only layer that can stop the
   file-reading tools (the hook only sees Bash command text).

2. **`guard.py` PreToolUse hook** — runs on every Bash call, dispatches to ordered
   policies (`secrets` → `destructive` → `gcp` → `db` → `github`), first match
   wins, unmatched → defer to normal permissions. Blocks destructive commands,
   secret reads, and dangerous domain operations by matching the command text.

3. **Scoped credentials** (see `infra/agent-credentials-runbook.md`) — the real
   guarantee. An agent physically cannot do what its credentials do not permit.

## Skills are guidance, not enforcement

`db-ops`, `gcp-ops`, `github-ops` under `.claude/skills/` carry operational
know-how. They load into the current context and do NOT create a separate identity
or an enforced toolset — verified: the hook sees `agent_type: <ABSENT>` for the main
session, and skills do not change that. Never treat a skill as a security boundary.
The boundary is layers 1–3 above.

## The known ceiling

Layers 1–2 are text-matching. They are defeated by indirection: a script that
shells out with different words, or a language SDK (`boto3`, `psycopg2`, `PyGithub`)
that never emits a `gcloud`/`psql`/`gh` string, sails through. Layer 3 (credentials)
is what actually closes that — and eventually capability-scoped tools (no shell path
to the dangerous action at all). Layers 1–2 make the safe path easy and the unsafe
path loud; they do not make it impossible.
```

- [ ] **Step 4: Add a short subsection to `CLAUDE.md`**

```markdown
### Ops skills & enforcement

Operational work against the database, GCP, or GitHub uses the `db-ops`, `gcp-ops`,
and `github-ops` skills under `.claude/skills/` — these carry the how-to. Enforcement
is separate and agent-agnostic: the `.claude/hooks/guard.py` PreToolUse hook, the
`permissions.deny` rules in `.claude/settings.json`, and the scoped credentials the
session runs under. Skills are guidance, never a security boundary. See
`.claude/hooks/README.md`.
```

- [ ] **Step 5: Run the full suite, then commit** (propose message, wait for approval)

```bash
python3 -m unittest discover -s .claude/hooks/tests -v   # all PASS
git add .claude/hooks/README.md .claude/hooks/tests/test_guard_integration.py CLAUDE.md
git commit -m "docs+test: record skills-first trust model and assert main-session enforcement"
```

---

## Phase 2 — Scoped credentials (the real backstop) — OPS RUNBOOK, human-executed

**Not a code phase.** These create real cloud/DB identities and need access an agent does not (and should not) have — several are blocked by our own hook by design. Deliverable: `infra/agent-credentials-runbook.md` with the artifacts below + a verification checklist.

**Critical constraint to state at the top of the runbook:** Claude Code has no native per-context credential isolation — a skill and the main session share the same process environment. So the win here is **session-level least privilege**: run the whole Claude Code session under scoped credentials, so no context (skill-driven or not) holds destructive capability. True per-context isolation is a Phase 3 (capability-tool / process-per-tool) property.

### 2a. Database — least-privilege roles

```sql
-- Run as a DB owner/superuser, once, per environment (local + prod).
CREATE ROLE db_agent_readonly WITH LOGIN PASSWORD '<placeholder-strong-secret>';
GRANT CONNECT ON DATABASE fashion_rental TO db_agent_readonly;
GRANT USAGE ON SCHEMA public TO db_agent_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO db_agent_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO db_agent_readonly;
-- Deliberately NO INSERT/UPDATE/DELETE, NO DDL, NO GRANT/REVOKE.
```

- Session read connection (`FASHION_RENTAL_DB_URL`) points at `db_agent_readonly`.
- Local-dev Flyway (the one permitted write) uses a separate migration-only credential, used solely by `./gradlew flywayMigrate` against `localhost:5433`.
- Prod (Supabase/Neon): a read-only role there too — never the owner/service role.

**Verify:** as `db_agent_readonly`, `SELECT` succeeds; `INSERT`/`UPDATE`/`DELETE`/`DROP` fail with `permission denied`.

### 2b. GCP — dedicated viewer service account

```bash
gcloud iam service-accounts create gcp-agent-sa \
  --display-name="Fashion Rental ops (read-mostly)"
PROJECT=<your-project-id>
SA=gcp-agent-sa@${PROJECT}.iam.gserviceaccount.com
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$SA" --role="roles/compute.viewer"
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$SA" --role="roles/logging.viewer"
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$SA" --role="roles/monitoring.viewer"
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$SA" --role="roles/billing.viewer"
# The one write — restart the container — is via SSH + docker on the VM, scoped by
# roles/compute.osLogin (or a per-instance binding), NOT compute.admin.
# DO NOT grant: *.admin, roles/owner, roles/editor, iam.serviceAccountAdmin.
```

**Verify:** as the SA, `gcloud compute instances describe` succeeds; `... delete` and `... create` fail.

### 2c. GitHub — fine-grained token + branch protection

- Fine-grained PAT (or App installation token) scoped to this repo: Issues (RW), Pull requests (RW), Actions (read + dispatch `ci.yml`), Contents (read). NOT: Administration, Workflows (write), org scopes.
- Enable branch protection on `main`: require PR + review, block direct/force push — GitHub becomes the real enforcer; the hook's push-to-main rule is defense-in-depth.
- Session `GH_TOKEN` is this token, never the user's full-access token.

**Verify:** with the token, `gh pr view` / `gh issue create` / `gh workflow run ci.yml` succeed; `gh repo delete` and a direct push to `main` fail.

### 2d. Runbook deliverable checklist

- [ ] `infra/agent-credentials-runbook.md` created with 2a–2c verbatim (placeholders only).
- [ ] The "no native per-context isolation → session-level least privilege" constraint stated at the top.
- [ ] Each subsection ends with its verification commands.
- [ ] A "rotation & revocation" note: how to rotate each credential and who owns it.

---

## Phase 3 — Capability-scoped tools (future) — DESIGN GATE

**Not scheduled.** Records the target and trigger for a later spec. No TDD tasks.

**Target:** replace policed Bash with narrow capability tools where the dangerous action is unreachable — `db-ops` becomes a `query(sql)` tool that runs read-only SQL server-side; `gcp-ops` becomes `get_logs()` / `describe_instance()` / `restart_backend()`; `github-ops` uses the GitHub MCP server with the Phase-2 token. Each runs as its own process with its own creds — where true per-context credential isolation finally becomes real. With tools in place, Bash leaves those paths and the hook becomes a thin backstop.

**Why it's the endgame:** the only model not defeated by an agent writing a script — there is no dangerous string to obfuscate because there is no shell route to the action.

**Trigger — start the Phase 3 spec when either holds:**
1. A 4th capability domain is added (Cloudflare R2, Sentry, a second cloud).
2. The workflow moves toward autonomous / deep-tree orchestration.

---

## Self-Review

**Coverage:**
- "Skills route, lighter" → Phase 1 (three skills, agents deleted, funnel + handoff dropped). ✓
- "Enforce via hooks" → Phase 0 keeps the five policies + env-deny; Task 5 asserts main-session enforcement. ✓
- "Enforce via credential management" → Phase 2 runbook. ✓
- "Verified: skills don't anchor identity" → recorded in README (Task 5) and Global Constraints. ✓
- Sustainability / future → Phase 3 gate. ✓

**Placeholder scan:** none — every skill body and every task carries full content; Phase 2 carries exact SQL/gcloud/token steps; Phase 3 is explicitly design-only. ✓

**Consistency:** post-Phase-0 the policy `check(tool_name, text, tool_input)` signature (three args, no `agent_type`) is used consistently in every reference; skill names `db-ops` / `gcp-ops` / `github-ops` match between file paths, frontmatter, README, and CLAUDE.md. ✓
