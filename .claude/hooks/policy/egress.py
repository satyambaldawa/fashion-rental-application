import re

# Network egress and artifact publishing: actions that send data or code out
# of this machine to somewhere else. Not silently allowed under guard.py's
# default-allow-for-Bash fallback — these get an explicit "ask" instead, so
# a human approves each time and an unattended subagent is auto-denied.

_LOCAL_HOST_RE = re.compile(r"(localhost|127\.0\.0\.1|::1)")

_PIPE_TO_SHELL_RE = re.compile(
    r"\b(curl|wget)\b[^\n]*\|\s*(sudo\s+)?(sh|bash|zsh)\b"
)

_CURL_WGET_RE = re.compile(r"\b(curl|wget)\b")
_DATA_SENDING_FLAG_RE = re.compile(
    r"(-d\b|--data(-binary|-raw|-urlencode)?\b|-F\b|--form\b|-T\b|--upload-file\b|"
    r"-X\s*['\"]?(POST|PUT|PATCH|DELETE)\b)"
)

_REMOTE_COPY_RE = re.compile(r"\b(scp|rsync|sftp)\b")

_PUBLISH_RE = re.compile(
    r"\b(npm|pnpm|yarn)\s+publish\b"
    r"|\bdocker\s+push\b"
    r"|\bmvn\s+deploy\b"
    r"|\bgradle(w)?\s+publish\w*\b"
    r"|\b(pip3?|twine)\s+upload\b"
    r"|\bcargo\s+publish\b"
    r"|\bgem\s+push\b"
)


def check(tool_name, text, tool_input):
    if not text:
        return None

    if _PIPE_TO_SHELL_RE.search(text):
        return (
            "ask",
            "Piping a downloaded script straight into a shell interpreter runs "
            "unreviewed remote code. Requires interactive confirmation — "
            "unattended subagents are auto-denied. Blocked by egress policy.",
        )

    if _CURL_WGET_RE.search(text) and _DATA_SENDING_FLAG_RE.search(text) and not _LOCAL_HOST_RE.search(text):
        return (
            "ask",
            "curl/wget is sending data to a non-local host, which could exfiltrate "
            "data. Requires interactive confirmation — unattended subagents are "
            "auto-denied. Blocked by egress policy.",
        )

    if _REMOTE_COPY_RE.search(text) and not _LOCAL_HOST_RE.search(text):
        return (
            "ask",
            "scp/rsync/sftp can copy files to/from a remote host. Requires "
            "interactive confirmation — unattended subagents are auto-denied. "
            "Blocked by egress policy.",
        )

    if _PUBLISH_RE.search(text):
        return (
            "ask",
            "This publishes an artifact to a registry — a supply-chain-facing "
            "action. Requires interactive confirmation — unattended subagents are "
            "auto-denied. Blocked by egress policy.",
        )

    return None
