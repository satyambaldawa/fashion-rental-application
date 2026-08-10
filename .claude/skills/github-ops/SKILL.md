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
