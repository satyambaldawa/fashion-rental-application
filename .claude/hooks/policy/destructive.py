import re

_SIMPLE_PATTERNS = [
    (
        re.compile(r"\bgit\s+push\b[^\n]*(--force\b|\s-f\b)"),
        "git push --force can overwrite remote history",
    ),
    (re.compile(r"\bgit\s+reset\s+--hard\b"), "git reset --hard discards local work irreversibly"),
    (re.compile(r"\bgit\s+clean\s+-\w*f\w*\b"), "git clean -f permanently deletes untracked files"),
    (re.compile(r"\bgit\s+branch\s+-D\b"), "git branch -D force-deletes a branch"),
    (re.compile(r"\bgit\s+filter-branch\b"), "git filter-branch rewrites history irreversibly"),
    (re.compile(r"\bdd\s+if="), "dd can overwrite raw disk devices"),
    (re.compile(r"\bmkfs(\.\w+)?\b"), "mkfs formats a filesystem, destroying its contents"),
    (re.compile(r"\b(shutdown|reboot)\b"), "shutdown/reboot is disruptive to a shared machine"),
    (re.compile(r"\bkill\s+-9\s+1\b"), "kill -9 1 kills PID 1 (init), crashing the machine"),
    (
        re.compile(r"\bchmod\s+-R\s+777\b"),
        "chmod -R 777 is an insecure, hard-to-reverse permission change",
    ),
]

_RM_SEGMENT_RE = re.compile(r"\brm\s+[^\n;&|]*")
_RECURSIVE_FLAG_RE = re.compile(r"(-[a-zA-Z]*r[a-zA-Z]*\b|--recursive\b)")
_FORCE_FLAG_RE = re.compile(r"(-[a-zA-Z]*f[a-zA-Z]*\b|--force\b)")


def _has_rm_rf(text):
    for match in _RM_SEGMENT_RE.finditer(text):
        segment = match.group(0)
        if _RECURSIVE_FLAG_RE.search(segment) and _FORCE_FLAG_RE.search(segment):
            return True
    return False


def check(tool_name, text, tool_input):
    if not text:
        return None
    if _has_rm_rf(text):
        return (
            "deny",
            "rm with both a recursive and a force flag is an irreversible bulk delete. "
            "Blocked by destructive-command policy.",
        )
    for pattern, why in _SIMPLE_PATTERNS:
        if pattern.search(text):
            return ("deny", f"{why}. Blocked by destructive-command policy.")
    return None
