import re

_FILE_TOOLS = {"Edit", "Write", "NotebookEdit"}

_WORKTREE_PATH_RE = re.compile(r"\.claude/worktrees/pr-[^/\s'\"]+")

_LIFECYCLE_ALLOW_RE = re.compile(r"\bgit\s+worktree\s+(add|remove|list|prune)\b")
_READ_ONLY_ALLOW_RE = re.compile(
    r"\b(cat|less|more|head|tail|grep|rg|find|ls|wc|file|stat|tree|awk|sed)\b"
)
_GIT_READ_ONLY_ALLOW_RE = re.compile(
    r"\bgit\s+(diff|log|show|status|blame|ls-files|ls-tree|rev-parse|fetch|branch)\b"
)
_GH_READ_ONLY_ALLOW_RE = re.compile(r"\bgh\s+pr\s+diff\b")

_SED_INPLACE_RE = re.compile(r"\bsed\s+(-\w*i\w*|--in-place)\b")

_WORKTREE_DENY_REASON = (
    "pr-review worktrees (.claude/worktrees/pr-*) are read-only — findings get reported "
    "as PR comments, never applied to the checkout. Blocked by pr-review policy."
)


def check(tool_name, text, tool_input):
    if tool_name in _FILE_TOOLS:
        file_path = str((tool_input or {}).get("file_path") or "")
        if _WORKTREE_PATH_RE.search(file_path):
            return ("deny", _WORKTREE_DENY_REASON)
        return None

    if not text or not _WORKTREE_PATH_RE.search(text):
        return None

    if _SED_INPLACE_RE.search(text):
        return ("deny", _WORKTREE_DENY_REASON)

    if (
        _LIFECYCLE_ALLOW_RE.search(text)
        or _GIT_READ_ONLY_ALLOW_RE.search(text)
        or _GH_READ_ONLY_ALLOW_RE.search(text)
        or _READ_ONLY_ALLOW_RE.search(text)
    ):
        return None

    return (
        "deny",
        "pr-review worktrees (.claude/worktrees/pr-*) are read-only and this command "
        "isn't on the recognized read/lifecycle allow-list (git worktree add/remove, "
        "git fetch, read-only git commands, or plain file-reading commands). Blocked by "
        "pr-review policy — inspect files with Read/Grep instead.",
    )
