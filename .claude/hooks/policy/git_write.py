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


def check(tool_name, text, tool_input):
    if not text:
        return None
    # Judge each chained segment on its own, so `git tag --list && git push`
    # can't hide the push behind a read-only first segment.
    verbs = [_mutating_verb(segment) for segment in re.split(r"&&|\|\||;|\||\n", text)]
    verb = next((v for v in verbs if v), None)
    if not verb:
        return None
    return (
        "ask",
        f"git {verb} can mutate the working tree, history, staging area, or a "
        "remote and isn't pre-approved. Requires interactive confirmation — "
        "unattended subagents are auto-denied. Blocked by git-write policy.",
    )
