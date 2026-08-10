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
