import re

_DENY_PATTERNS = [
    (re.compile(r"\bgcloud\s+compute\s+instances\s+delete\b"), "deletes the GCP VM"),
    (
        re.compile(r"\bgcloud\s+compute\s+instances\s+stop\b"),
        "stopping the VM risks the static-IP billing edge case documented in "
        "infra/deployment-plan.md",
    ),
    (re.compile(r"\bgcloud\s+compute\s+addresses\s+delete\b"), "deletes the static IP"),
    (re.compile(r"\bgcloud\s+compute\s+firewall-rules\s+delete\b"), "deletes a firewall rule"),
    (re.compile(r"\bgcloud\s+compute\s+disks\s+delete\b"), "deletes a disk"),
    (re.compile(r"\bgcloud\s+projects\s+delete\b"), "deletes the GCP project"),
    (
        re.compile(r"\bgcloud\s+iam\s+service-accounts\s+(delete|keys\s+delete)\b"),
        "deletes an IAM identity or key",
    ),
    (re.compile(r"\bdocker\s+rm\b"), "removes a container"),
    (re.compile(r"\bdocker\s+stop\b"), "stops a container"),
    (
        re.compile(r"\bdocker\s+system\s+prune\b[^\n]*--volumes\b"),
        "prunes docker volumes, destroying data",
    ),
    (re.compile(r"\bdocker\s+volume\s+rm\b"), "removes a docker volume"),
]

_ALLOW_PATTERNS = [
    re.compile(r"\bgcloud\s+compute\s+instances\s+(describe|list|get-serial-port-output)\b"),
    re.compile(r"\bgcloud\s+compute\s+(networks|firewall-rules)\s+(list|describe)\b"),
    re.compile(r"\bgcloud\s+billing\b"),
    re.compile(r"\bdocker\s+logs\b"),
    re.compile(r"\bdocker\s+ps\b"),
    re.compile(r"\bdocker\s+restart\s+fashion-rental-backend\b"),
]


def check(tool_name, text, tool_input):
    if not text:
        return None
    for pattern, why in _DENY_PATTERNS:
        if pattern.search(text):
            return ("deny", f"Blocked by GCP policy: this command {why}.")
    for pattern in _ALLOW_PATTERNS:
        if pattern.search(text):
            return ("allow", "Matches the GCP read/approved-write allow-list.")
    return None
