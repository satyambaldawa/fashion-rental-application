---
description: Draft a GitHub issue from a rough idea — scope, decisions, acceptance criteria, out-of-scope — light enough that work-issue-auto's own Plan stage still does the real planning, not a rubber stamp of this ticket.
argument-hint: [rough description of the feature or bug]
model: sonnet
---

# /raise-ticket $1

You are drafting a GitHub issue from a rough idea, for a human to review before it's created.

## Why this stays lightweight

`/work-issue` and `/work-issue-auto` both run a **Plan** stage (a dedicated subagent that
produces the file-by-file implementation plan) and then review *that plan* with three
independent personas (`devils-advocate`, `tech-lead`, `business-lead`). If this ticket already
contains a full plan, two bad things happen: the Plan stage either re-derives it from scratch
(wasted work) or copies it verbatim (and the personas end up reviewing this ticket's guesses
instead of forming an independent judgment). So this command drafts **only** what a planner
can't infer by reading the code — scope, already-decided constraints, and acceptance criteria —
and deliberately leaves the how to the pipeline that reviews it properly.

## Configuration

- **Repository:** `satyambaldawa/fashion-rental-application` (owner `satyambaldawa`).
- **Labels:** never invent one. Fetch the live set with `gh label list --repo
  satyambaldawa/fashion-rental-application` and pick from what actually exists.
- **This command never applies `ready-for-deployment`** as part of the normal create step —
  see stage 3 below. That label is `/work-issue-auto`'s sole trigger per `CLAUDE.md`'s
  automated-pipeline carve-out, and it gets its own explicit yes/no, never bundled into the
  create approval.
- **This command never touches the project board.** Moving a card to `Ready` is `/work-issue`'s
  job at intake, not this command's.

## Operating rules

- **Confirm before creating, always.** Draft, show, wait for approval — no exceptions, per
  `CLAUDE.md` and the repo convention of confirming before creating/commenting on issues.
- **`$1` is the user's own idea, in this same session** — not untrusted external input. If it's
  too vague to draft from (no clear feature/bug, no clear boundary), ask a clarifying question
  instead of guessing.
- **The body excludes:** step-by-step implementation plans, file-by-file edit lists, code
  snippets, API request/response shapes, and test-case tables. The one exception: naming a
  file or pattern that's a genuine pre-existing decision worth pinning down (e.g. "follows the
  exact pattern already used for `/gallery`") — that's a decision, not a plan, and it's exactly
  the kind of ambiguity that turns into a persona Blocker later if left unstated.
- **Use `gh issue create`, not raw `gh api`.** This command runs in an interactive local
  session (a human is present to approve), unlike `/work-issue-auto`'s unattended cloud runs —
  `gh`'s high-level GraphQL-backed subcommands work fine here. See `.claude/skills/github-ops`
  for the allowed command set.
- **Body via file, not inline.** Write the drafted body to a scratch file and pass
  `--body-file <path>` — `guard.py`'s PreToolUse hook denies any Bash command whose *text*
  contains DDL keywords (`ALTER`, `DROP`, `TRUNCATE`, …), which can trip on an inline heredoc
  if the body ever quotes a migration or schema detail.

## Pipeline

### 0. Gather context
1. Take the rough idea from `$1`. If empty or too vague to draft from, ask for one before
   continuing.
2. Check `features/README.md` for a matching story area. If one clearly matches, read that
   story file — reuse its terminology and any decisions it already recorded rather than
   re-deriving them.
3. Run `gh issue list --repo satyambaldawa/fashion-rental-application --state all --search
   "<keywords from the idea>"` to check for a likely duplicate. If a strong match turns up,
   surface it and ask whether to continue before drafting.
4. Run `gh label list --repo satyambaldawa/fashion-rental-application` to get the live label
   set.

### 1. Draft
5. Infer:
   - **Title** — conventional-commit-style prefix (`feat:`, `fix:`, `test:`, `docs:`, `chore:`)
     matching the style of existing issue titles.
   - **Labels** — type label plus a priority label only if the idea clearly implies one; pick
     only from the set fetched in stage 0.
   - **Body**, with exactly these sections (omit any that don't apply):
     - `## Scope` — what's being built or fixed, in prose plus bullets. Include only
       already-decided constraints: an existing pattern to follow, the module/file a change
       belongs in, an existing service/entity to reuse, whether a new Flyway migration is
       needed. Never a step-by-step plan.
     - `## Acceptance criteria` — a checkbox list, concrete and testable, in the style of
       issue #153 (`- [ ] Visiting /about while logged out renders...`).
     - `## Out of scope` — explicit exclusions. This is the section that prevents scope creep
       turning into a persona Blocker downstream — be as specific here as in Scope.
     - `## Reference` — one line, only if a `features/*.md` story or a related issue/PR number
       clearly applies.
6. Present the full draft — title, labels, body — in a fenced markdown block in chat.

### 2. Refine
7. 🛑 **GATE — wait for approval or edit requests.** Apply requested edits and re-present; loop
   until approved. Do not proceed on silence or an ambiguous reply.

### 3. Create
8. On explicit approval: write the body to a scratch file, then
   ```
   gh issue create --repo satyambaldawa/fashion-rental-application --title "<title>" \
     --body-file <path> --label "<label1>" --label "<label2>"
   ```
9. Report the created issue's number and URL.
10. 🛑 **GATE — separately** (never bundled with step 7's approval): ask whether to also apply
    `ready-for-deployment` now. Only on an explicit yes:
    ```
    gh issue edit <N> --repo satyambaldawa/fashion-rental-application --add-label ready-for-deployment
    ```

## What this command does not do

- Does not write a plan, a file list, or any code.
- Does not create or edit files under `features/` — it only reads one for reference.
- Does not move a project-board card.
- Does not apply `ready-for-deployment` as part of the default flow.
