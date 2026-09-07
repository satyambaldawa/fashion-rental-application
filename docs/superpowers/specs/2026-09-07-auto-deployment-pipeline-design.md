# Design: Automated `ready-for-deployment` → PR Pipeline

**Date:** 2026-09-07
**Status:** Draft for review
**Type:** Claude Code workflow (RemoteTrigger cloud routine + new headless slash command +
`CLAUDE.md` policy amendment)

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
| `/work-issue-auto` slash command | `.claude/commands/work-issue-auto.md` | Headless sibling of `/work-issue`. Same pipeline stages; every 🛑 gate replaced by an auto-proceed/halt rule. Does not modify `/work-issue`. |
| RemoteTrigger routine | claude.ai cloud (`claude.ai/code/routines`) | Hourly cron. Finds queued issues, claims one, invokes `/work-issue-auto`, checks in-progress issues for staleness. |
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

## 4. Trigger: RemoteTrigger routine

- **Schedule:** hourly cron (e.g. `"7 * * * *"` — off the top of the hour). **This is the API's
  minimum interval** — sub-hourly cron expressions are rejected server-side. Expected latency
  between labeling an issue and the pipeline starting: **up to ~1 hour**, not the ~15 minutes
  floated earlier in discussion — that number was wrong and is corrected here.
- **Environment:** `claude-code-default` (`env_01JUULxEv2kv7pAtwckBkZKX`, `anthropic_cloud`) — the
  only environment currently available on this account.
- **Repo:** `session_context.sources[0].git_repository.url` =
  `https://github.com/satyambaldawa/fashion-rental-application`. Write access (branch, push, open
  PR, label swap, comment) is backed by the already-installed Claude GitHub App, scoped to this
  repo only.
- **Routine prompt (each poll), in order:**
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

- **Up to ~1 hour trigger latency** — accepted trade-off of RemoteTrigger's hourly cron floor vs.
  a GitHub Actions event-driven alternative that was considered and explicitly declined twice
  during design (favoring no new GitHub Actions secret/workflow over lower latency).
- **Subagent dispatch inside a CCR routine session is unverified.** `/work-issue` relies heavily
  on the `Agent` tool to dispatch persona reviewers, `fullstack-craftsman`, `Diagnose`/`Fix`,
  `Coverage`, and `Docs` as subagents. The `schedule` skill's example `allowed_tools` list
  (`Bash, Read, Write, Edit, Glob, Grep`) does not show `Agent` as confirmed-available in a cloud
  routine session. **Must verify at implementation time** — if unavailable, `/work-issue-auto`
  needs to run those stages inline in one session rather than as dispatched subagents, which is a
  materially different (and less isolated) implementation.
- **`gh` CLI auth inside the sandboxed cloud environment is unverified.** The routine needs to
  swap labels, post comments, and open PRs — either via `gh` (needs it pre-authenticated in the
  CCR sandbox via the installed GitHub App) or via direct GitHub API calls with a token available
  in that environment. **Must verify at implementation time.**
- **Halt comments must be loud and specific** — a vague "something failed" comment defeats the
  purpose; must include actual test output or the persona's actual Blocker text, not a summary.
- **No cap on daily auto-processed issues** — deliberately out of scope (see §10): the queue is
  bounded by how many issues a human actually labels, and sequential processing already throttles
  throughput to one per hour.

---

## 10. Out of scope (YAGNI)

- No parallel processing of multiple issues.
- No auto-retry of a halted issue — always requires a human to notice and re-label.
- No auto-resume of a partially-completed run — a stale/halted issue always starts fresh from
  `/work-issue-auto`'s step 0 if re-labeled (see §7 of the parent `/work-issue` design for why:
  reusing a partial branch's implicit state without the original session's memory is unsafe).
- No changes to `/work-issue` itself, its three persona agents, or its manual gates.
- No auto-merge — identical to `/work-issue`, this pipeline only ever reaches "PR open."
- No GitHub Actions workflow — considered and declined in favor of RemoteTrigger (§4).

---

## 11. Files to be created / changed

```
.claude/
  commands/
    work-issue-auto.md          ← new, headless sibling of work-issue.md
CLAUDE.md                        ← add the carve-out from §7
docs/superpowers/specs/
  2026-09-07-auto-deployment-pipeline-design.md   ← this file
```

Plus one RemoteTrigger routine created directly via the API/`schedule` skill (not a repo file) —
its config (cron, prompt, environment_id, repo URL) is fully specified in §4.

---

## 12. Open questions (verify during implementation, not blocking spec approval)

- Whether the `Agent` tool is available inside a CCR routine session (§9) — determines whether
  `/work-issue-auto` can dispatch subagents or must run every stage inline.
- Whether `gh` is pre-authenticated in the sandboxed environment, or whether raw GitHub API calls
  with an explicit token are needed instead.
- Exact cron minute to use (any non-`:00` value is fine; pick one during implementation).
