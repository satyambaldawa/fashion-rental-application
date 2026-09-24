import re

# Privilege escalation, credential rotation, and system/persistence changes:
# nothing in this repo's dev workflow legitimately needs these unattended.
# Not silently allowed under guard.py's default-allow-for-Bash fallback —
# these get an explicit "ask" instead, so a human approves each time and an
# unattended subagent is auto-denied.

_SUDO_SU_RE = re.compile(r"\bsudo\b|\bsu\b")
_SSH_KEYGEN_RE = re.compile(r"\bssh-keygen\b")
_CLOUD_AUTH_RE = re.compile(
    r"\bgcloud\s+auth\b|\baws\s+configure\b|\baz\s+login\b"
)
_NPM_LOGIN_RE = re.compile(r"\bnpm\s+(login|adduser)\b")
_GPG_KEY_RE = re.compile(r"\bgpg\b[^\n]*--(gen-key|delete-key|delete-secret-key|full-gen-key)\b")

_CRONTAB_EDIT_RE = re.compile(r"\bcrontab\s+-e\b")
_SYSTEMCTL_RE = re.compile(r"\bsystemctl\s+(enable|start|restart|unmask)\b")
_LAUNCHCTL_RE = re.compile(r"\blaunchctl\s+(load|start|bootstrap)\b")
_FIREWALL_RE = re.compile(r"\biptables\b|\bufw\s+(enable|allow|deny|reload)\b")

_CHECKS = (
    (_SUDO_SU_RE, "sudo/su escalates privileges"),
    (_SSH_KEYGEN_RE, "ssh-keygen can overwrite existing SSH keys"),
    (_CLOUD_AUTH_RE, "this changes which cloud identity is active"),
    (_NPM_LOGIN_RE, "this rotates npm registry credentials"),
    (_GPG_KEY_RE, "this generates or deletes a GPG key"),
    (_CRONTAB_EDIT_RE, "editing the crontab can establish persistence"),
    (_SYSTEMCTL_RE, "this enables/starts a system service"),
    (_LAUNCHCTL_RE, "this loads/starts a launchd job"),
    (_FIREWALL_RE, "this changes machine-wide firewall rules"),
)


def check(tool_name, text, tool_input):
    if not text:
        return None
    for pattern, why in _CHECKS:
        if pattern.search(text):
            return (
                "ask",
                f"{why} and isn't pre-approved. Requires interactive confirmation "
                "— unattended subagents are auto-denied. Blocked by privilege policy.",
            )
    return None
