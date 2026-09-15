---
description: End-to-end build-and-ship a GitHub issue — branch, plan, multi-persona plan review, build, test, harden, PR, move card to review. Checkpoint-heavy.
argument-hint: [issue-number]
model: sonnet
---

# /work-issue $1

You are the **orchestrator** for issue **#$1**. Drive it end-to-end through the pipeline below.

## Configuration (where to look)

- **Repository:** `satyambaldawa/fashion-rental-application` (owner `satyambaldawa`, repo
  `fashion-rental-application`). Issue **#$1** is an issue in this repo.
- **Project board:** GitHub Projects v2, owner (user) **`satyambaldawa`**, **project number `1`** —
  `https://github.com/users/satyambaldawa/projects/1`.
- **Status column names:** the board's single-select **Status** field currently has the options
  `Backlog`, `Ready`, `In progress`, `In review`, `Done` — note the lowercase second word. Read the
  options rather than hardcoding them; a pure case difference is the same column, not a missing one.
  If a genuinely different set comes back, STOP and ask — don't guess a column.
- **Finding the card:** the issue's card is the project item on board #1 whose **content is issue
  #$1** of this repo. Resolve it with `gh api graphql` (read the project's items / the board's Status
  field + options, match the item to issue #$1), then update that item's Status.

## Operating rules (read first)

- **Checkpoint-heavy.** At every 🛑 gate you MUST stop, present the state, and **wait for the user's
  reply**. Never cross a 🛑 on your own. The user is the decision-maker.
- **You coordinate; subagents do the heavy work.** Dispatch each step to a subagent on the model
  named in `[brackets]`. Run the three review personas **in parallel** (multiple Agent calls in one
  message). You run on Sonnet — synthesize verdicts, relay, and execute; don't re-derive.
- **GitHub via `gh`, never the GitHub MCP.** Use the `gh` CLI for issue reads, project-card moves
  (`gh api graphql` for Projects v2), and the PR. Use plain `git` for local branch/commit/push. The
  `github` MCP server is **not** used by this workflow — do not look for it, and do not stop if it
  is absent. See `.claude/skills/github-ops` for the safe command set. If `gh` itself is missing or
  unauthenticated (`gh auth status`), STOP and tell the user.
- **`gh` and heredocs:** the `guard.py` PreToolUse hook denies any Bash command whose *text* contains
  DDL keywords (`ALTER`, `DROP`, `TRUNCATE`, …) — including inside a heredoc. A PR body that quotes
  a migration will therefore be refused. Write the body to a file and use `gh pr create --body-file`.
- **Subagents are non-interactive.** A subagent that triggers a permission prompt is killed mid-call
  and returns an empty result, which reads exactly like a model failure. Consequences:
  - **There is no `Grep` or `Glob` tool in this environment.** Subagents search with read-only
    `Bash` (`grep -rn`, `find`, `ls`, `cat`, `git log/diff/show`), which is allow-listed in
    `.claude/settings.json`. Keep that allowlist intact or every review dies again.
  - **Anything outside the read-only set, you must hand them.** Before dispatching a review, write
    the plan (and, in the post-build loop, `git diff`) to a file and pass the **path** in the
    prompt. Never assume a subagent can run a build or a test.
  - If a subagent returns nothing or only a preamble, treat it as **infrastructure failure, not a
    verdict** — say so at the gate and never present its absence as an approval. Inspect its
    transcript under `~/.claude/projects/<project>/<session>/subagents/agent-<id>.jsonl`; a final
    `tool_use` with no matching `tool_result` means it hit a permission prompt.
- **Honor the repo's rules** in `CLAUDE.md` (no secrets, no unrequested scope, money = INTEGER, etc.).
- **Never push to `main`.** Work happens on a feature branch; the finale is a PR.

## Pipeline

### 0. Intake + branch
1. Fetch issue #$1 with `gh issue view $1 --repo <owner/repo> --json number,title,body,labels`: title,
   body, acceptance criteria. If the body references a feature story (e.g. `US-301`), read that file
   under `features/`. If it references a parent epic or a plan/spec document, read those too — and
   check whether they live on a branch other than `main`, in which case copy them somewhere readable
   before you switch branches.
2. Move the issue's card on board #1 → **Ready** (see Configuration → Finding the card). If the move
   fails, surface it — don't skip it.
3. Create a feature branch off `main`: `git checkout main && git pull && git checkout -b <type>/issue-$1-<slug>`.

### 1. Baseline
4. Run backend + frontend tests: `cd backend && ./gradlew test` and `cd frontend && pnpm test`.
5. If the baseline is red (should be rare): dispatch **Diagnose [sonnet]** to explain the failures.
   🛑 **GATE — present the diagnosis, ask if/how to fix.** On the user's instruction, dispatch a
   **Fix [opus]** subagent, then re-run tests until green.
6. Move the card → **In Progress**.

### 2. Plan
7. Dispatch a **planning subagent [opus]** (Plan/architect or general-purpose) to produce a
   **plan + scope of changes**: what will change, in which modules/files, the approach, the test
   plan, and any risks. This is a document, not code — no edits yet.

### 3. Persona review of the PLAN (parallel)
8. Dispatch **in parallel**, passing each the plan + the issue requirements:
   - `devils-advocate` **[opus]**
   - `tech-lead` **[sonnet]**
   - `business-lead` **[sonnet]**
9. Collect the three verdicts. Synthesize into one consolidated list: **Blockers** (union of all
   must-fix) and **Suggestions**.
   🛑 **GATE — present the synthesis; ask for course-corrections to the plan.** Apply what the user
   directs to the plan document.
   🛑 **GATE — ask: run Pass 2 (re-review the revised plan) or skip to build?**

### 4. Pass 2 (only if chosen)
10. Re-dispatch the three personas on the revised plan → synthesize →
    🛑 **GATE — present verdicts, ask for course-corrections.**

### 5. Build
11. Dispatch **fullstack-craftsman [sonnet]** to implement the approved plan exactly — following
    repo conventions, no unrequested scope.
12. Run backend + frontend tests. If red: dispatch **Diagnose [sonnet]** →
    🛑 **GATE — ask if/how to fix** → **Fix [opus]** → re-run until green. A fix must state what it
    changed and why, and must never weaken a test to go green.
    🛑 **GATE — ask: re-evaluate the built code (loop the three personas back to step 8, this time
    reviewing the code diff instead of the plan) or continue?**

### 6. Coverage
13. Dispatch **Coverage [sonnet]**: assess coverage of the changed code (esp. billing, availability,
    transactions — critical paths get 100%). Add meaningful missing tests; re-run to confirm green.
    Do not write tautological tests to chase a number.

### 7. Docs
14. Dispatch **Docs [haiku]**: update docs only if the change requires it (README env/run steps,
    ADRs in `technical-architecture.md`, feature notes). Skip if nothing needs it.
    🛑 **GATE — present a summary of all changes; ask for approval to commit.**

### 8. Ship
15. On approval: `git add -A && git commit` with a message stating the *why* (reference #$1). Then
    🛑 **GATE — ask for approval to push + open PR.**
16. On approval: `git push -u origin <branch>`, then open a PR with
    `gh pr create --base main --head <branch> --title ... --body-file <path>` (body referencing #$1
    and summarizing the work + the review outcomes). Use `--body-file`, not an inline heredoc — see
    the `guard.py` note in the operating rules.

### 9. Hand off
17. Move the issue's card → **In Review**. Report the PR URL and a short summary of what shipped,
    what the personas flagged, and what was fixed.

## Guardrails recap
- Stop at every 🛑. Push only to a feature branch, never `main`. GitHub via `gh`, never the GitHub
  MCP. No secrets. No scope creep. Verify tests are actually green (show output) before claiming so.
- A subagent that returns nothing is a **failed dispatch, not a passed review.** Report it as such.
