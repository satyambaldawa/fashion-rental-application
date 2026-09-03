---
name: pr-review
description: Use when the user gives a GitHub PR link or number for this repo and wants it reviewed against this repo's language-specific checklists (Java, TypeScript/React) — checks out the PR in an isolated worktree, applies the matching checklist per changed file, and validates findings with the user before posting any as inline PR comments.
---

# PR Review

Checklist-driven PR review, modular by language. Different from the generic `code-review`
skill: this one always checks out a real worktree for full-file context (not just the raw
diff), and routes each changed file to a dedicated checklist under `checklists/` based on
its extension.

## Workflow

**1. Resolve the PR.** Accept either a full URL (`https://github.com/<owner>/<repo>/pull/<n>`)
or a bare number (use `gh repo view --json nameWithOwner -q .nameWithOwner` for the current
repo). Confirm you have `<owner>/<repo>` and `<number>` before continuing.

**2. Check it out in an isolated worktree.**
```bash
git fetch origin "pull/<number>/head:pr-<number>-review"
git worktree add .claude/worktrees/pr-<number> pr-<number>-review
```
This works even for fork PRs — GitHub exposes `pull/<n>/head` against the base repo's
`origin` remote regardless of where the branch actually lives.

**3. Get the diff and changed-file list.**
```bash
gh pr diff <number>              # full unified diff, anchors hunks to file:line
gh pr diff <number> --name-only  # changed files, for checklist routing
```

**4. Route each changed file to its checklist** by extension (table below). A PR touching
both stacks loads both checklists, each applied only to its own files. Files with no
matching checklist are skipped — say so, don't invent checks for them.

| Extension | Checklist |
|---|---|
| `.java` | `checklists/java.md` |
| `.ts`, `.tsx`, `.js`, `.jsx` | `checklists/typescript.md` |

**5. Review.** For each changed file, read the diff hunk (what changed) plus the full file
from the worktree (`.claude/worktrees/pr-<number>/<path>`) for surrounding context — a
SOLID or test-coverage finding often depends on code outside the diff hunk itself. Apply
every relevant check from the routed checklist(s). Anchor every finding to `file:line`
using the line number in the PR's version of the file (right side of the diff).

**6. Present findings for validation — never skip this step.** List every finding grouped
by file, numbered, each with: category, `file:line`, one-line summary, suggested fix. Ask
the user which to keep and which to discard. Do not decide this yourself and do not post
anything until the user has responded.

**7. Post only the confirmed findings, as one review, after one more explicit confirmation.**
Show the exact comments you're about to post, then:
```bash
gh api repos/<owner>/<repo>/pulls/<number>/reviews --method POST --input - <<'EOF'
{"event":"COMMENT","comments":[{"path":"<path>","line":<n>,"side":"RIGHT","body":"<finding>"}]}
EOF
```
If GitHub rejects a `line`/`side` pair (line not part of the diff), fall back to computing
the legacy `position` from the diff hunk for that file rather than guessing a nearby line.
Report the resulting review URL back to the user.

**8. Clean up.**
```bash
git worktree remove .claude/worktrees/pr-<number>
```
Leave the local `pr-<number>-review` branch ref in place — the repo's guard hook hard-blocks
`git branch -D` (force-delete) with no exceptions, and `git branch -d` (safe delete) will
refuse for any branch not yet merged into the current HEAD, which is normal for an open
PR. The stray ref is harmless; don't fight the guard to remove it.

## Hard rules

- **The worktree is read-only, enforced by the guard hook, not just by convention.**
  `.claude/hooks/policy/pr_review.py` denies every `Edit`/`Write`/`NotebookEdit` call
  targeting a path under `.claude/worktrees/pr-*`, plus any Bash command against that path
  that isn't on its read-only/lifecycle allow-list (`git worktree add|remove`, `git fetch`,
  `cat`/`grep`/`find`/etc., read-only `git`/`gh` commands) — `rm`, `sed -i`, `git commit`,
  shell redirects, and anything unrecognized are blocked by default. `permissions.deny` in
  `.claude/settings.json` backs this up independently for the file tools. Review findings
  by reading; never try to "fix" the PR's code directly in the worktree — the hook will
  reject it, and even if it didn't, that's not this skill's job.
- `event` is always `"COMMENT"` — never `APPROVE` or `REQUEST_CHANGES`. Merge decisions are
  a human action per this repo's CLAUDE.md; that boundary applies here even though the
  guard hook's regex only pattern-matches `gh pr review --approve`, not `gh api` calls with
  an approve event.
- Step 6 is not optional and is not a formality — do not proceed to step 7 without the
  user's explicit per-finding or per-batch confirmation.
- Never post a finding the user discarded, and never silently add findings after the
  validation step.

## Adding a new language checklist

Drop a new `checklists/<language>.md` following the same section structure as
`checklists/java.md`, then add its extension(s) to the routing table above.

## Common mistakes

- Reviewing only the diff hunk and missing context that lives elsewhere in the file (e.g.
  a class that's already too large, or a constant already defined elsewhere) — always read
  the full file from the worktree.
- Treating `gh api ... reviews` as auto-approved because it's not on the guard hook's deny
  list — it still requires the user's explicit confirmation per CLAUDE.md.
- Leaving the worktree or temp branch behind after the review finishes.
