import re

_DENY_PATTERNS = [
    (re.compile(r"\bgh\s+repo\s+delete\b"), "deletes the GitHub repository"),
    (re.compile(r"\bgh\s+pr\s+merge\b"), "merges are a human, manual action per CLAUDE.md"),
    (
        re.compile(r"\bgh\s+pr\s+review\b[^\n]*--approve\b"),
        "approving a PR is a step toward merging that CLAUDE.md reserves for humans",
    ),
    (
        re.compile(
            r"\bgh\s+workflow\s+run\s+(cd\.yml|infra-provision\.yml|cleanup-gcp-ssh-keys\.yml)\b"
        ),
        "triggers a production-affecting workflow",
    ),
    (
        re.compile(r"\bgh\s+api\b[^\n]*(-X\s*DELETE|--method\s+DELETE)"),
        "issues a DELETE via the GitHub API",
    ),
    (
        re.compile(r"\bgh\s+api\b[^\n]*branches/[^/\s]+/protection[^\n]*(-X\s*DELETE|--method\s+DELETE)"),
        "deletes branch protection",
    ),
    (
        re.compile(r"\bgit\s+push\b[^\n]*\borigin\b[^\n]*\bmain\b"),
        "direct push to main bypasses PR review",
    ),
    (
        re.compile(r"\bgit\s+push\b[^\n]*\borigin\b\s+HEAD\b", re.IGNORECASE),
        "pushing HEAD to origin could target main depending on the current checked-out branch",
    ),
    (
        re.compile(r"^\s*git\s+push\s*(origin|-u\s+origin|--set-upstream\s+origin)?\s*(;|&|\||$)"),
        "a bare git push with no explicit branch relies on tracking config, which could point at main",
    ),
]

_ALLOW_PATTERNS = [
    re.compile(r"\bgh\s+issue\s+(list|view|create|comment)\b"),
    re.compile(r"\bgh\s+pr\s+(list|view|comment|review)\b"),
    re.compile(r"\bgh\s+run\s+(list|view|watch)\b"),
    re.compile(r"\bgh\s+workflow\s+run\s+ci\.yml\b"),
    re.compile(r"\bgh\s+workflow\s+(list|view)\b"),
]


def check(tool_name, text, tool_input):
    if not text:
        return None
    for pattern, why in _DENY_PATTERNS:
        if pattern.search(text):
            return ("deny", f"Blocked by GitHub policy: this command {why}.")
    for pattern in _ALLOW_PATTERNS:
        if pattern.search(text):
            return ("allow", "Matches the GitHub read/approved-write allow-list.")
    return None
