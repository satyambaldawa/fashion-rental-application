import os
import re

# Every git subcommand that mutates the working tree, history, staging area,
# or a remote gets an explicit "ask" here. This includes add/commit/push:
# under guard.py's old defer-by-default model they could stay unmatched and
# still end up gated (by falling through to settings.json's prompt), but
# guard.py now defaults unmatched Bash calls to *allow* — so "unmatched"
# would mean "silently allowed" instead. Asking explicitly is what actually
# preserves "a human must actively approve this" for all six, matching
# CLAUDE.md's git-approval rule: prompts the human in an interactive
# session, auto-denies a subagent that can't answer.
#
# Truly irreversible forms (reset --hard, clean -f, branch -D, push --force,
# filter-branch) are already hard-denied by destructive.py/github.py — deny
# still wins over ask, so there's no conflict, just belt-and-suspenders on
# the milder forms (e.g. plain `git reset`, `git clean -n`) those checkers
# don't cover.
_MUTATING_VERB_RE = re.compile(
    r"\bgit\s+(add|commit|push|checkout|restore|reset|clean|stash|merge|"
    r"rebase|cherry-pick|revert|rm|mv|tag|remote|submodule|apply|am|switch|"
    r"worktree)\b"
)


# Listing forms of otherwise-mutating subcommands only read state.
_READ_ONLY_FORM_RE = re.compile(
    r"^git\s+("
    r"tag(\s+(-l|--list)\b.*)?"
    r"|remote(\s+(-v|--verbose|show|get-url)\b.*)?"
    r"|stash\s+(list|show)\b.*"
    r"|worktree\s+list\b.*"
    r")$"
)


def _mutating_verb(segment):
    segment = segment.strip()
    match = _MUTATING_VERB_RE.search(segment)
    if not match or _READ_ONLY_FORM_RE.match(segment):
        return None
    return match.group(1)


# #96's CLAUDE.md carve-out: applying `ready-for-deployment` to an issue is
# advance, scoped authorization for /work-issue-auto to commit, push a feature
# branch, and open a PR for that issue without further approval. That text
# change alone doesn't reach this hook, though — an unattended cloud session
# (no human present to answer an "ask") still stalled on every checkout/
# add/commit/push. This exempts exactly those four verbs, gated on a single
# signal now: home directory is /root or under /home/ (local interactive
# sessions are always under /Users/...).
#
# This dropped a second, AND-ed signal (CLAUDE_PROJECT_DIR unset) that three
# straight live-run failures traced back to: #91/#92 established that ad-hoc
# `claude --cloud` sessions leave CLAUDE_PROJECT_DIR unset, and that got
# generalized here to "cloud sessions leave it unset" — but a live routine
# run's own embedded diagnostic (added specifically to settle this without a
# fourth guess) showed CLAUDE_PROJECT_DIR *set*, to the repo path, in a real
# routine session. The generalization was simply wrong for routines
# specifically; ad-hoc and routine cloud sessions are not the same
# environment. HOME has been independently confirmed as /root four separate
# ways (a shell-snapshot path, the egress proxy's CA bundle path, a direct
# `echo $HOME` a human ran interactively, and this hook's own diagnostic) and
# never once been wrong — it's the one signal actually worth keying on.
#
# Every other mutating verb (reset, clean, merge, rebase, stash, rm, tag,
# remote, ...) still asks unconditionally, in every session. Pushing to or
# merging main stays hard-denied regardless of this allow — github.py's deny
# patterns for that run as a separate checker, and deny always wins over
# allow in guard.py's dispatch order (see guard.py's decide()).
_PIPELINE_SAFE_VERBS = {"add", "checkout", "commit", "push"}


def _cloud_session_signals():
    """Returns (is_cloud, debug_str). Kept as a pair (not just a bool) so the
    ask path can still report what this check saw — cheap insurance after
    three straight wrong guesses about what actually distinguishes a cloud
    session here."""
    home = os.path.expanduser("~")
    normalized = home.rstrip("/") or "/"
    is_cloud = normalized == "/root" or normalized.startswith("/home/")
    debug = f"HOME={home!r} normalized={normalized!r}"
    return is_cloud, debug


def check(tool_name, text, tool_input):
    if not text:
        return None
    # Judge each chained segment on its own, so `git tag --list && git push`
    # can't hide the push behind a read-only first segment.
    verbs = [_mutating_verb(segment) for segment in re.split(r"&&|\|\||;|\||\n", text)]
    matched = [v for v in verbs if v]
    if not matched:
        return None
    is_cloud, debug = _cloud_session_signals()
    if is_cloud and all(v in _PIPELINE_SAFE_VERBS for v in matched):
        return (
            "allow",
            "Cloud session performing only add/checkout/commit/push — exempted per "
            "#96's automated-pipeline carve-out. Pushing to or merging main is still "
            "hard-denied by github.py regardless of this allow.",
        )
    verb = matched[0]
    return (
        "ask",
        f"git {verb} can mutate the working tree, history, staging area, or a "
        "remote and isn't pre-approved. Requires interactive confirmation — "
        "unattended subagents are auto-denied. Blocked by git-write policy. "
        f"[cloud-exemption check: {debug}]",
    )
