import re

_FILE_TOOLS = {"Edit", "Write", "NotebookEdit"}

_WORKTREE_PATH_RE = re.compile(r"\.claude/worktrees/pr-[^/\s'\"]+")

_HEREDOC_START_RE = re.compile(r"<<-?\s*(['\"]?)(\w+)\1")

_MUTATION_RE = re.compile(
    r"\bgit\s+(add|commit|checkout|reset|clean|restore|apply|am|rebase|merge|rm|mv|stash)\b"
    r"|\b(rm|mv|cp|tee|chmod|chown|truncate|dd|mkdir|rmdir|perl|python[23]?|node)\b"
    r"|\bsed\s+(-\w*i\w*|--in-place)\b"
)
_REDIRECT_RE = re.compile(r"(?<![&\d])>>?(?!&)")

_WORKTREE_DENY_REASON = (
    "pr-review worktrees (.claude/worktrees/pr-*) are read-only — this command would "
    "mutate the checkout. Findings get reported as PR comments, never applied to the "
    "checkout. Blocked by pr-review policy."
)


def _strip_heredocs(text):
    """Drop heredoc bodies so prose/data they carry (a PR description, a JSON
    payload) can't be mistaken for a command operating on the worktree path."""
    lines = text.split("\n")
    kept = []
    i = 0
    while i < len(lines):
        line = lines[i]
        kept.append(line)
        match = _HEREDOC_START_RE.search(line)
        if match:
            delimiter = match.group(2)
            i += 1
            while i < len(lines) and lines[i].strip() != delimiter:
                i += 1
            i += 1  # also drop the delimiter line itself
            continue
        i += 1
    return "\n".join(kept)


def check(tool_name, text, tool_input):
    if tool_name in _FILE_TOOLS:
        file_path = str((tool_input or {}).get("file_path") or "")
        if _WORKTREE_PATH_RE.search(file_path):
            return ("deny", _WORKTREE_DENY_REASON)
        return None

    if not text:
        return None

    command_text = _strip_heredocs(text)
    if not _WORKTREE_PATH_RE.search(command_text):
        return None

    if _MUTATION_RE.search(command_text) or _REDIRECT_RE.search(command_text):
        return ("deny", _WORKTREE_DENY_REASON)

    return None
