# Design: `/work-issue` — End-to-End Build-and-Ship Workflow

**Date:** 2026-07-26
**Status:** Draft for review
**Type:** Claude Code workflow (slash command + persona subagents + Projects v2 wiring)

---

## 1. Purpose

A single command, `/work-issue <id>`, that takes a GitHub issue from the board and drives it
end-to-end: branch → plan → multi-persona plan review → build → test → harden → ship (PR) →
move card to review. It is **checkpoint-heavy** — the human approves at every meaningful gate —
and **model-optimized** — each step runs on the cheapest model that does that step well.

The defining idea: **the personas review the PLAN, not the finished code.** Course-correction
happens on paper, before implementation, where it is cheapest. The same personas can optionally
re-review the built code once, on demand.

This is an **end-to-end build-and-ship** command, not merely a review pipeline.

---

## 2. Components to build

| Component | Location | Role |
|---|---|---|
| `/work-issue` slash command | `.claude/commands/work-issue.md` | The orchestrator. Holds the ordered procedure, dispatches subagents, pauses at gates. Pinned to **Sonnet 5** via frontmatter. |
| `devils-advocate` agent | `.claude/agents/devils-advocate.md` | Adversarial reviewer. Model: **Opus 4.8**. |
| `tech-lead` agent | `.claude/agents/tech-lead.md` | Architecture/quality reviewer. Model: **Sonnet 5**. |
| `business-lead` agent | `.claude/agents/business-lead.md` | Domain/requirements reviewer; reads `fashion-rental-discovery.md`. Model: **Sonnet 5**. |
| **GitHub MCP** (activate existing `github` plugin) | remote-hosted `https://api.githubcopilot.com/mcp/`, PAT via profile | All GitHub operations: fetch issue, move Projects v2 card (projects toolset), open PR. |

Existing agents (`code-reviewer`, `technical-architect`, `fullstack-craftsman`) are **not**
modified. The feature build (step 5) may delegate to `fullstack-craftsman` running on Sonnet 5.

**GitHub via MCP, not `gh`.** All GitHub interactions go through the hosted GitHub MCP (already
installed as the `github` plugin), not the `gh` CLI — typed tools instead of string-parsing.
Local git (branch/commit/push) remains plain git. See §7.

---

## 3. The pipeline

```
/work-issue <id>
 0. gh: fetch issue + linked requirements · move card → READY · create branch off main
 1. Baseline: run FE + BE tests (ensure a green start)
       IF fail → Diagnose [Sonnet 5] → 🛑 GATE: you decide if/how to fix → Fix [Opus 4.8] → re-run
    move card → IN PROGRESS
 2. PLAN: author plan + scope of changes                       [Opus 4.8]
 3. Personas judge the PLAN vs requirements (dispatched in parallel):
       😈 Devil's advocate [Opus 4.8]
       🏗️ Tech lead        [Sonnet 5]
       💼 Business lead     [Sonnet 5]
    Orchestrator synthesizes verdicts →
       🛑 GATE: course-correct the plan
       🛑 GATE: Pass 2 (re-judge plan) — or — skip to build
 4. (If Pass 2) personas re-judge the revised plan → 🛑 GATE: course-correct
 5. BUILD the approved plan                                    [Sonnet 5, via fullstack-craftsman]
       Run FE + BE tests → IF fail → Diagnose [Sonnet 5] → 🛑 GATE: decide → Fix [Opus 4.8] → re-run
       🛑 GATE: re-evaluate the built code (loop personas back to step 3, target = code) — or — continue
 6. Coverage: check + add missing tests                        [Sonnet 5]
 7. Docs: update if needed                                     [Haiku 4.5]
       🛑 GATE: approve commit
 8. Commit to branch + push + open PR                          🛑 GATE: approve push/PR
 9. move card → IN REVIEW
```

**Review targets.** The personas are reused against two different inputs:
- **Steps 3–4:** the *plan* (pre-build). Primary, always runs.
- **Step 5 loop (optional):** the *built code diff* (post-build). Only if you choose "re-evaluate."

**Loop structure = A (critique-first).** Passes 3–4 gather critique without changing code; the
build in step 5 implements the vetted plan once. The step-5 loop is the only place code is
re-reviewed, and only on request.

---

## 4. Model map

| Work | Model | Rationale |
|---|---|---|
| Orchestrator (`/work-issue`) | Sonnet 5 | Coordinates + relays verdicts; real decisions sit at human gates. Prescriptive command keeps it reliable. |
| Plan authoring | Opus 4.8 | Highest-leverage thinking; a good plan de-risks everything downstream. |
| 😈 Devil's advocate | Opus 4.8 | Adversarial rigor — the highest-value Opus spend. |
| 🏗️ Tech lead | Sonnet 5 | Architecture/quality judgment, cost-balanced. |
| 💼 Business lead | Sonnet 5 | Domain-rule check against discovery doc. |
| Feature build | Sonnet 5 | Plan is already vetted and explicit; Sonnet is sufficient. |
| Test diagnosis | Sonnet 5 | Debugging sweet spot. |
| Test fix (post human-gate) | Opus 4.8 | Fixes are correctness-critical and human-approved. |
| Coverage / add tests | Sonnet 5 | Test writing. |
| Doc updates | Haiku 4.5 | Mechanical prose edits. |

---

## 5. Persona charters

Each persona receives: **the plan + scope of changes**, **the issue requirements** (issue body +
linked feature story), and (business-lead) `fashion-rental-discovery.md`. Each returns a
**structured verdict**: blockers (must-fix), suggestions (author's call), and an overall
approve/revise recommendation. They judge the plan; they do not edit it.

- **😈 Devil's advocate (Opus 4.8).** Assume the plan is wrong. Hunt for: missed edge cases,
  race conditions, transactional gaps, availability/double-booking holes, snapshot-pricing
  mistakes, security holes, brittle assumptions, and "what breaks under load / retries / partial
  failure." Rewarded for finding real problems, not nitpicks.

- **🏗️ Tech lead (Sonnet 5).** Judge against the repo's conventions and architecture: module
  layout, `ApiResponse<T>` envelope, DTO/model rules, mapper/number-service patterns, Flyway
  naming + `ddl-auto: validate`, money-as-INTEGER, `OffsetDateTime`/IST, SOLID, testability.
  Flags scope creep and unrequested refactoring (CLAUDE.md forbids it).

- **💼 Business lead (Sonnet 5).** Judge against domain requirements in the issue and
  `fashion-rental-discovery.md`: does the plan satisfy the acceptance criteria, respect rental
  rules (deposits, late-fee tiers, package component reservation), and match how the single
  owner/staff actually uses the tablet PWA? Flags requirement gaps and misread acceptance
  criteria.

---

## 6. Human gates (checkpoint-heavy)

The orchestrator **stops and waits for your input** at each:

1. **Test-fix decision** (step 1 baseline, and step 5 post-build) — after diagnosis, you decide if/how to fix.
2. **Plan course-correction** (step 3, and step 4 if Pass 2) — after verdicts are synthesized.
3. **Pass 2 or skip** (step 3) — run a second plan-review round or go build.
4. **Re-evaluate built code or continue** (step 5) — optional post-build persona review.
5. **Approve commit** (step 7).
6. **Approve push + PR** (step 8) — honours CLAUDE.md's "always ask before git actions" and "no direct pushes to main."

---

## 7. GitHub integration (via GitHub MCP)

Board: `https://github.com/users/satyambaldawa/projects/1` (user-scoped Projects v2).
All GitHub calls use the **hosted GitHub MCP** (`github` plugin), authenticated by
`GITHUB_PERSONAL_ACCESS_TOKEN` supplied through the `frclaude` profile.

**Status transitions (via the MCP projects toolset):**
- **→ Ready** at kickoff (step 0)
- **→ In Progress** after baseline (step 1)
- **→ In Review** after the PR is opened (step 9)

The MCP's projects toolset (`projects_get` to resolve the board's fields/options, `projects_write`/
`update_project_item` to set the Status single-select field) handles moves directly — **no manual
GraphQL / one-time ID capture needed** (the earlier `gh` approach is dropped). Exact tool names are
resolved at runtime from whatever the active `github` MCP exposes; the command references the
projects toolset by role, not by hardcoded name, to stay version-robust. `gh api graphql` remains a
documented fallback only if the MCP projects tools prove insufficient at runtime.

**Entry point:** `/work-issue 42` → GitHub MCP `get_issue` for the requirements. If the issue links
a feature story (e.g. US-301), the orchestrator reads that story file too.

**Auth setup (one-time, user action).** Create a PAT with **`repo` + `project`** scopes (fine-grained:
Contents, Pull requests, Issues read/write + Projects read/write). Store it as
`GITHUB_PERSONAL_ACCESS_TOKEN` in `~/.config/fashion-rental/default.env` (outside the repo). This
also activates the `github` MCP, which is enabled but currently inactive for lack of the token.

---

## 8. Risks & caveats (accepted)

- **Long, multi-agent runs.** Even checkpoint-heavy, a full run spans many subagent dispatches
  (plan, 3 personas ×1–2 passes, build, diagnosis/fix, coverage, docs). This is the cost of the
  requested rigor. Cost is contained by keeping Opus to plan + devil's advocate + test-fix only.
- **Orchestrator does verdict synthesis** — genuine reasoning on Sonnet 5. Mitigated by human
  gates (you decide) and a prescriptive command.
- **Auto-fix boundaries.** Test fixes are gated by a human decision before they run, so the
  workflow never silently weakens a test to go green. The fix step must explain *what* it changed
  and *why*, and re-run tests to prove green.
- **Status-move failures must be loud.** If a card move fails (renamed column, missing scope,
  MCP hiccup), the orchestrator must surface it at a gate, not silently proceed.
- **GitHub MCP is remote + needs a real secret.** It runs on GitHub's infra (fine — it's GitHub's
  own data) and requires a PAT with `repo` + `project` scopes. The PAT lives only in the
  out-of-repo profile; it is never committed. A too-narrow token = failed project moves.
- **Model availability.** Model IDs assumed: `claude-opus-4-8`, `claude-sonnet-5`,
  `claude-haiku-4-5`. Subagent model override enum: `opus | sonnet | haiku | fable`.

---

## 9. Out of scope (YAGNI)

- No Jira/Linear support — GitHub Issues + Projects v2 only.
- No automatic merge — the workflow stops at "In Review" with an open PR; a human merges.
- No parallelizing multiple issues in one run.
- No new persona beyond the three; no changes to existing agents.
- No auto-push to `main` — ever.

---

## 10. Files to be created (no code yet)

```
.claude/
  commands/
    work-issue.md          ← orchestrator (frontmatter: model: sonnet-5, argument-hint, allowed-tools)
  agents/
    devils-advocate.md     ← model: opus
    tech-lead.md           ← model: sonnet
    business-lead.md       ← model: sonnet
docs/superpowers/specs/
  2026-07-26-work-issue-workflow-design.md   ← this file
```

No board-ID capture files (GitHub MCP resolves fields internally). One user action outside these
files: add `GITHUB_PERSONAL_ACCESS_TOKEN` to `~/.config/fashion-rental/default.env` to activate the
`github` MCP.

---

## 11. Open questions

None blocking. To confirm during implementation:
- Exact slash-command frontmatter field for pinning the model (verify against current Claude Code).
- Exact GitHub MCP projects-toolset tool names at runtime (command references them by role, so
  robust to naming; confirm once the MCP is active with the PAT).
- Whether the issue → feature-story link is by convention (issue references `US-xxx`) or manual.
```
