# Design: Automated `ready-for-deployment` → PR Pipeline

**Date:** 2026-09-07 (revised 2026-09-08 after empirical spikes)
**Status:** Draft for review
**Type:** Claude Code workflow (cloud routine + new headless slash command + `CLAUDE.md` policy
amendment)

> **Revision note (2026-09-08).** Sections 4, 9, and 12 were rewritten after seven cloud-session
> spikes. The original draft assumed facts that turned out to be false. What was actually measured
> is in §13; treat that section as the authority wherever it disagrees with older prose.

---

## 1. Purpose

Today, shipping an issue means a human runs `/work-issue <id>` and answers ~6 approval gates by
hand. This design adds an **unattended** path: label a GitHub issue `ready-for-deployment` and,
within about an hour, either a PR appears (built, tested, reviewed by the three personas) or the
issue is flagged with exactly what's blocking it — with no human needed to reach that first PR.

This is **additive**, not a replacement. `/work-issue` continues to exist unchanged for
interactive, manually-driven work. This design reuses its pipeline shape (branch → plan → persona
review → build → test → coverage → docs → PR) but swaps every human checkpoint for an
auto-proceed-on-green / halt-on-red rule, because an unattended session has no one to wait on.

---

## 2. Components to build

| Component | Location | Role |
|---|---|---|
| **Hook fix** | `.claude/settings.json` and/or `.claude/hooks/guard.py` | **Prerequisite.** Makes `guard.py` run correctly in cloud sessions. Nothing else works until this lands. See §13. |
| `/work-issue-auto` slash command | `.claude/commands/work-issue-auto.md` | Headless sibling of `/work-issue`. Same pipeline stages; every 🛑 gate replaced by an auto-proceed/halt rule. Does not modify `/work-issue`. |
| Routine | claude.ai cloud (`claude.ai/code/routines`) | Hourly cron, the only trigger. Runs the staleness watchdog, claims one queued issue, invokes `/work-issue-auto`. |
| `CLAUDE.md` policy carve-out | `CLAUDE.md` | Scoped exception documenting that the `ready-for-deployment` label is advance, per-issue authorization for `/work-issue-auto` to commit/push/open a PR without a chat approval — merge still always manual. |
| Claude GitHub App | already installed | Confirmed installed 2026-09-07, scoped to `satyambaldawa/fashion-rental-application` only. Backs both repo checkout and write access (issues, PRs, actions, workflows) for the cloud routine's git operations. No further setup needed. |

Existing `/work-issue` and its three persona agents (`devils-advocate`, `tech-lead`,
`business-lead`) are reused as-is by the routine's session — not modified, not duplicated.

---

## 3. Label state machine

```
ready-for-deployment   (human applies; queued)
        │  routine claims it (next hourly poll)
        ▼
auto-in-progress        (pipeline running)
        │
        ├─ success ──────────► label removed, PR opened, card → In Review
        │
        └─ halt (any reason) ► auto-failed
                                (comment explains why; branch left pushed;
                                 no PR; no auto-retry — human resumes via
                                 /work-issue <n> or re-labels after fixing
                                 the underlying cause)
```

Only issues currently labeled `ready-for-deployment` are eligible to be claimed. Once claimed,
the label swap to `auto-in-progress` removes them from that query — this is what prevents a
second poll from double-claiming the same issue while it's still running.

---

## 4. Trigger: hourly schedule on the routine

**One routine, one trigger: an hourly cron.** Nothing else. No GitHub Actions workflow, no API
`/fire` endpoint, no bearer token, no repository secret. The routine polls for work and also runs
the staleness watchdog on the same pass.

- **Cadence:** hourly, at a non-`:00` minute (e.g. `"7 * * * *"`). **One hour is the API's minimum
  interval** — sub-hourly cron expressions are rejected server-side. An earlier draft claimed
  ~15-minute polling was possible; that was wrong.
- **Latency:** up to ~1 hour between labeling an issue and the pipeline starting. Accepted
  deliberately in exchange for having no GitHub-side machinery and no secret to manage.

**Why polling rather than a GitHub event.** Routines *do* support native GitHub triggers, but only
for **Pull request** and **Release** events. Issue events — including `issues.labeled`, the premise
of this pipeline — are not supported (verified 2026-09-08). A trigger on `issues.labeled` would
therefore require a separate GitHub Actions workflow to act as a doorbell into the routine's `/fire`
endpoint. That design was considered and **declined**: it buys seconds-instead-of-an-hour latency at
the cost of a workflow file, a repository secret, and a second failure domain. For a single-owner
shop where a one-hour turnaround is irrelevant, the simpler system wins.

If latency ever matters, the doorbell can be added later without changing anything else in this
design — the routine's work is identical either way.
- **Environment:** `claude-code-default` (`env_01JUULxEv2kv7pAtwckBkZKX`, `anthropic_cloud`) — the
  only environment currently available on this account. Image measured 2026-09-08: **Ubuntu 24.04,
  Python 3.11.15, git and node present, running as root, `gh` NOT installed** (see §13).
- **Repo:** `session_context.sources[0].git_repository.url` =
  `https://github.com/satyambaldawa/fashion-rental-application`. Write access (branch, push, open
  PR, label swap, comment) is backed by the already-installed Claude GitHub App, scoped to this
  repo only. **Pushing requires the App to be installed on the specific repository** — a push to a
  repo without it fails with `403`, measured 2026-09-08. The App is installed on
  `fashion-rental-application`, so this pipeline is unaffected; an earlier draft's claim that any
  visible repo would work was wrong.
- **Routine prompt (each run), in order:**
  1. **Watchdog pass:** list issues labeled `auto-in-progress`. For each, resolve when that label
     was actually applied via the issue's **Timeline/Events API**
     (`GET /repos/{owner}/{repo}/issues/{issue}/timeline`, find the most recent `labeled` event
     for `auto-in-progress`) — GitHub does not expose a per-issue label-applied timestamp on the
     issue object itself, only on its event history. Any issue claimed more than **45 minutes**
     ago with no resolution (still `auto-in-progress`, no PR, no `auto-failed`) is stale: flip to
     `auto-failed`, post the standard stale-pipeline comment (see §6), and stop — this transition
     happens once, since the issue leaves the `auto-in-progress` query the moment it's flipped, so
     an ongoing outage doesn't spam repeat comments on subsequent hourly polls.
  2. **Claim pass:** list issues labeled `ready-for-deployment`. If any exist, claim exactly the
     first (oldest) one — swap label to `auto-in-progress` — and invoke the `/work-issue-auto <n>`
     pipeline for it, synchronously, within this same routine session. Sequential only: never
     claim a second issue in the same poll, even if more are queued — they wait for the next
     hourly poll after this one finishes or halts.

---

## 5. `/work-issue-auto <n>` pipeline

Same stages as `/work-issue` (`.claude/commands/work-issue.md`), with every 🛑 gate mapped to an
unattended rule:

| Stage | Manual gate today | Auto-mode rule |
|---|---|---|
| Baseline tests | 🛑 decide if/how to fix | Red → 1 automatic Diagnose+Fix attempt → still red → **halt** |
| Plan authored | — | unchanged (Opus) |
| Persona review of plan | 🛑 course-correct; 🛑 Pass 2 or skip | Any Blocker → **halt** (no Pass 2 round — there's no human to decide to run one). Suggestions are logged into the eventual PR description, not blocking. |
| Build | 🛑 re-evaluate code or continue | No post-build persona re-review loop in auto mode (keeps scope to what the vetted plan already covers) |
| Build tests | 🛑 decide if/how to fix | Red → 1 automatic Diagnose+Fix attempt → still red → **halt** |
| Coverage | — | unchanged (Sonnet) |
| Docs | 🛑 approve commit | Proceeds automatically if everything above is green |
| Commit + push + PR | 🛑 approve push+PR | Proceeds automatically — this is the step the `CLAUDE.md` carve-out (§7) authorizes |
| Hand off | — | Card → In Review, PR link commented on the issue, unchanged |

**Claim-time tracking comment.** Immediately after claiming (label swap, before branch creation),
the session posts a comment on the issue:

> 🤖 Automated pipeline started — track live progress: `https://claude.ai/code/session_<id>`

Every cloud session knows its own session URL from the start. This comment serves two purposes:
live tracking while it runs, and a post-mortem artifact if the watchdog later marks the issue
`auto-failed` — the original session's URL remains visible on the issue even though the halt
comment itself is posted by a *different* (later) routine invocation.

---

## 6. Halt behavior

Any of: baseline tests red after 1 fix attempt · a persona Blocker · build tests red after 1 fix
attempt · watchdog staleness (§4.1). On halt:

1. Label → `auto-failed` (removed from both the `ready-for-deployment` and `auto-in-progress`
   queries — never auto-retried).
2. Comment on the issue stating what halted it and why (test output, Blocker summary, or the
   staleness notice), and noting whether a branch with partial/pushed work exists.
3. **No PR is opened.** `main` is never touched, exactly as in the manual pipeline.
4. Resuming is a human decision: fix the underlying cause, then either re-apply
   `ready-for-deployment` (routine picks it up fresh next poll) or run `/work-issue <n>` locally
   to finish it by hand, reusing the existing branch if one exists.

---

## 7. `CLAUDE.md` policy carve-out

`CLAUDE.md` currently requires explicit in-chat approval before every commit, push, or PR. This
design's premise is that **applying the `ready-for-deployment` label to a specific issue is that
approval, given in advance and scoped to that issue** — the automated path never merges, never
touches `main`, and halts (no PR) on anything red or Blocked. The user confirmed this carve-out
during design review (2026-09-07). Implementation must add wording to `CLAUDE.md`'s
"Always Ask Before Git Actions" section along these lines:

> **Exception — automated pipeline.** `/work-issue-auto`, triggered only by a human applying the
> `ready-for-deployment` label to a specific issue, may commit, push a feature branch, and open a
> PR for that issue without an additional in-chat approval — the label is the advance, scoped
> authorization. It must still never push to `main` and never merge; halting instead of opening a
> PR is always the default on any test failure or persona Blocker.

---

## 8. Idempotency & concurrency

- **Idempotency:** the label swap *is* the claim. An issue is only ever in one of
  `ready-for-deployment` / `auto-in-progress` / `auto-failed` / (none, PR open) at a time, and the
  routine's two passes (§4) only ever act on issues in the relevant state — no separate database
  or marker file needed.
- **Concurrency:** strictly sequential, one issue in flight at a time, by design choice (avoids
  parallel branches racing on shared files/tests, keeps spend predictable). A backlog of multiple
  labeled issues drains one per hour.

---

## 9. Risks & caveats (accepted)

- **BLOCKER — the repo's own `PreToolUse` hook disables cloud sessions.** See §13. This must be
  fixed before any part of this pipeline can run. It is the first task of the implementation plan.
- **`gh` is not installed in the cloud image.** The pipeline needs to read issues, swap labels,
  post comments, and open PRs. Options, in preference order: (1) call the GitHub REST API with
  `curl` using the session's git credentials, (2) install `gh` via the cloud environment's setup
  script, (3) attach a GitHub MCP connector. Resolved during implementation; the plan tests (1)
  first because it adds no environment state.
- **Secrets are present in cloud-session environment variables.** A probe run on 2026-09-08
  captured a live messaging token, session/account/org UUIDs, and the account email from `env`.
  The routine prompt must never dump `env` into a file, a commit, or an issue comment. Any
  diagnostic output committed by the pipeline must be an explicit allow-list of fields, never a
  wholesale environment dump.
- **Daily routine run cap.** Routines have a per-account daily run cap separate from subscription
  usage. A busy label day can exhaust it; runs are then rejected until the window resets. The
  watchdog (§4) treats an issue stuck in `auto-in-progress` from a rejected run as stale, so this
  degrades to a visible `auto-failed`, not a silent stall.
- **Up to ~1 hour trigger latency**, and up to ~1 hour more per queued issue, since work is
  sequential. Accepted deliberately (§4) in exchange for a system with no GitHub-side machinery and
  no secret to manage. A backlog of five labeled issues takes roughly five hours to drain.
- **Halt comments must be loud and specific** — a vague "something failed" comment defeats the
  purpose; must include actual test output or the persona's actual Blocker text, not a summary.
- **No cap on daily auto-processed issues** — deliberately out of scope (see §10): the queue is
  bounded by how many issues a human actually labels, and sequential processing already throttles
  throughput.

---

## 10. Out of scope (YAGNI)

- No parallel processing of multiple issues.
- No auto-retry of a halted issue — always requires a human to notice and re-label.
- No auto-resume of a partially-completed run — a stale/halted issue always starts fresh from
  `/work-issue-auto`'s step 0 if re-labeled (see §7 of the parent `/work-issue` design for why:
  reusing a partial branch's implicit state without the original session's memory is unsafe).
- No changes to `/work-issue` itself, its three persona agents, or its manual gates.
- No auto-merge — identical to `/work-issue`, this pipeline only ever reaches "PR open."
- No GitHub Actions workflow and no repository secret — considered and declined in favour of
  hourly polling (§4). Can be added later without changing anything else if latency ever matters.

---

## 11. Files to be created / changed

```
.claude/
  settings.json                 ← hook command fix (prerequisite, §13)
  hooks/
    guard.py                    ← possibly, depending on which cause in §13.3
    README.md                   ← document the cloud-session gap
    tests/
      test_guard_integration.py ← regression test for the cloud-session case
  commands/
    work-issue-auto.md          ← new, headless sibling of work-issue.md
CLAUDE.md                        ← add the carve-out from §7
docs/superpowers/specs/
  2026-09-07-auto-deployment-pipeline-design.md   ← this file
```

Plus one routine created via the API/`schedule` skill (not a repo file) — its config is in §4 — and
three GitHub labels. **No GitHub Actions workflow and no repository secret.**

---

## 12. Open questions

- Which of the two candidate causes in §13.3 is the real one. Both have different fixes. Task 1 of
  the implementation plan resolves this empirically before any fix is written.
- Which GitHub access method the cloud session should use, given `gh` is absent (see §9).
- Exact cron minute for the secondary schedule trigger (any non-`:00` value; pick during
  implementation).

---

## 13. Measured facts (spikes, 2026-09-07 → 2026-09-08)

Seven cloud sessions were run to establish these. They supersede any conflicting statement earlier
in this document.

### 13.1 What works in a cloud routine session

| Capability | Result |
|---|---|
| `Agent` tool / subagent dispatch | ✅ works (3.4 s round trip) |
| `Read` tool | ✅ works |
| Bash, in a repository with no hooks | ✅ works — full shell, git, node |
| Image | Ubuntu 24.04, Python 3.11.15, running as root |
| `gh` CLI | ❌ **not installed** |
| Push to a repo without the Claude GitHub App | ❌ `403` |

### 13.2 The blocker

In **this** repository, every tool matched by the `PreToolUse` hook in `.claude/settings.json` —
`Bash`, `Edit`, `Write`, `NotebookEdit` — fails with `Tool execution was interrupted.` Tools not
matched by the hook (`Read`, `Agent`) work normally. A failing `PreToolUse` hook denies its tool,
so `guard.py` is failing closed and blocking everything, including `echo`.

Proven two independent ways:

1. **Tool discrimination.** In one session: `Read .claude/settings.json` ✅, `Read guard.py` ✅,
   `Write TOOLTEST.txt` ❌. Read and Write are both ordinary file operations; the only difference
   between them is the hook matcher.
2. **Hook-free control.** The identical probe against `satyambaldawa/whatsaap_automation` (no
   `.claude/` directory, same environment, same config) ran Bash successfully.

The failure reproduces through both the raw routine API and `claude --cloud`, so it is not an
artifact of hand-written job config.

### 13.3 Eliminated vs. remaining causes

Eliminated by measurement: `python3` missing (3.11.15 present) · `guard.py` not executable (tracked
`100755`) · `policy` package missing from the clone (all seven files tracked) · a too-narrow
`allowed_tools` list · the platform lacking a shell.

Still open, with different fixes:

- **(a)** `CLAUDE_PROJECT_DIR` is unset in cloud sessions, so the configured command
  `${CLAUDE_PROJECT_DIR}/.claude/hooks/guard.py` expands to `/.claude/hooks/guard.py`, which does
  not exist. Verified locally that the expansion collapses this way when the variable is empty.
- **(b)** Project hooks from a cloned repository are not trusted in an unattended session, so
  hooked tools are denied rather than running an unapproved hook. If this is the cause, no path
  change helps.

### 13.4 Design constraint this creates

Whatever the fix, it must not simply make `guard.py` fail open. The hook is one of three
enforcement layers described in `.claude/hooks/README.md`, and silently disabling it in exactly the
environment that runs unattended — where no human reviews each command — is the worst possible
place to weaken it. Acceptable fixes make the hook *run correctly* in cloud sessions. If that
proves impossible, the fallback is to keep it failing closed and not run this pipeline in cloud
sessions at all.

`README.md` also needs a correction: it claims layers 1–2 "make the safe path easy and the unsafe
path loud." In a cloud session they currently make every path impossible, silently. That gap should
be documented there.
