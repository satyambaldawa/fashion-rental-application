# Agent safety net — how it works and what it does NOT guarantee

Three enforcement layers, all agent-agnostic (they do not care whether a skill,
a subagent, or the main session issued the command):

1. **`permissions.deny` in `.claude/settings.json`** — blocks the Read/Edit/Grep/
   Write/NotebookEdit tools from touching `.env*`, `*key.json`,
   `*service-account*`, and anything under `.claude/worktrees/pr-*` (the
   `pr-review` skill's checkouts). Highest precedence, evaluated before hooks —
   this still fires even if `guard.py` crashes or is misconfigured.

2. **`guard.py` PreToolUse hook** — registered for `Bash`, `Edit`, `Write`, and
   `NotebookEdit`. Runs every policy (`secrets`, `destructive`, `gcp`, `db`,
   `github`, `pr_review`) and collects all their verdicts — **deny always wins**
   over allow, and allow wins over the default defer-to-normal-permissions.
   This matters for chained commands: `gh pr view 86 && rm ...` would otherwise
   let `github`'s allow-list for `gh pr view` launder the `rm` straight past
   every other policy just because it ran first in the dispatch order. Blocks
   destructive commands, secret reads, dangerous domain operations, and — via
   `pr_review` — any edit/write, or Bash command shaped like a mutation (`rm`,
   `mv`, `sed -i`, `git add|commit|checkout|reset|clean|restore`, shell
   redirects, …), targeting a `pr-review` worktree. `pr_review` strips heredoc
   bodies before matching, so a PR description or JSON payload that merely
   *mentions* the worktree path or a command like `sed -i` as prose can't
   trip it — only text that's actually part of the invoked command counts.

3. **Scoped credentials** (see `infra/agent-credentials-runbook.md`) — the real
   guarantee. An agent physically cannot do what its credentials do not permit.

`permissions.deny` and `guard.py` overlap deliberately for `.claude/worktrees/pr-*`
edits/writes: the former is a static backstop that survives a hook bug, the
latter also catches Bash-based mutations (`rm`, `sed -i`, `git commit`, shell
redirects) that no `permissions.deny` glob could ever see.

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
