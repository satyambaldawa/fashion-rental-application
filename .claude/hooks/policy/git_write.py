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
# add/commit/push. This exempts exactly those four verbs, and only when BOTH
# cloud-session signals agree: home directory under /home/ (every observed
# cloud/routine run; local sessions are always under /Users/...) AND
# CLAUDE_PROJECT_DIR unset (the #91/#92 signal). Requiring both, rather than
# either alone, means an uncertain or wrong read of either signal fails
# toward "still ask" — never toward "silently allow" — on a local session.
# Every other mutating verb (reset, clean, merge, rebase, stash, rm, tag,
# remote, ...) still asks unconditionally, in every session. Pushing to or
# merging main stays hard-denied regardless of this allow — github.py's deny
# patterns for that run as a separate checker, and deny always wins over
# allow in guard.py's dispatch order (see guard.py's decide()).
_PIPELINE_SAFE_VERBS = {"add", "checkout", "commit", "push"}


def _is_cloud_session():
    home = os.path.expanduser("~")
    # Confirmed via a live routine run's own shell-snapshot path
    # ($HOME/.claude/shell-snapshots/...) that this environment's actual
    # $HOME is /root, not /home/<something> as originally assumed from cwd
    # alone (cwd and $HOME are not the same thing, and conflating them was
    # the bug: the exemption below never fired on a real run, reproducing
    # the exact stall this fix exists to remove). /home/ is kept as a second
    # accepted pattern -- genuinely observed via cwd, just not yet confirmed
    # as $HOME in any run -- rather than removed, since either shape is a
    # real, evidenced possibility and neither weakens the check.
    home_is_cloud_shaped = home == "/root" or home.startswith("/home/")
    project_dir_unset = not os.environ.get("CLAUDE_PROJECT_DIR")
    return home_is_cloud_shaped and project_dir_unset


def check(tool_name, text, tool_input):
    if not text:
        return None
    # Judge each chained segment on its own, so `git tag --list && git push`
    # can't hide the push behind a read-only first segment.
    verbs = [_mutating_verb(segment) for segment in re.split(r"&&|\|\||;|\||\n", text)]
    matched = [v for v in verbs if v]
    if not matched:
        return None
    if _is_cloud_session() and all(v in _PIPELINE_SAFE_VERBS for v in matched):
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
        "unattended subagents are auto-denied. Blocked by git-write policy.",
    )
