import re

ENV_DUMP_RE = re.compile(r"(^|[;&|]\s*)(env|printenv|export)\s*(\s|;|&|\||$)")
DOTENV_FILE_RE = re.compile(
    r"\b(cat|less|more|head|tail|grep|vi|vim|nano|code|open)\b[^\n]*\.env(\.[a-zA-Z0-9_-]+)?\b"
)
KEY_FILE_RE = re.compile(
    r"\b(cat|less|more|head|tail|grep)\b[^\n]*(key\.json|service-account)"
)
SECRET_VAR_RE = re.compile(r"\$\{?([A-Za-z_][A-Za-z0-9_]*)\}?")
SECRET_NAME_RE = re.compile(
    r"(PASSWORD|SECRET|TOKEN|_KEY|KEY_ID|DSN|DATABASE_URL|_URL)", re.IGNORECASE
)
GCLOUD_SECRET_RE = re.compile(r"\bgcloud\s+secrets\s+(versions\s+access|describe)\b")
GH_SECRET_RE = re.compile(r"\bgh\s+secret\s+(list|get|set|delete)\b")
DOCKER_INSPECT_RE = re.compile(r"\bdocker\s+inspect\b|\bdocker\s+exec\b[^\n]*\benv\b")
PROC_ENVIRON_RE = re.compile(r"/proc/\S+/environ")
DB_CLIENT_DSN_ARG_RE = re.compile(
    r"(?:^|[;&|]\s*)(?:psql|pg_dump|pg_restore)[ \t]+[\"']?\$\{?[A-Za-z_][A-Za-z0-9_]*\}?[\"']?"
)

SAFE_VAR_NAMES = {
    "VITE_API_URL",
    "SPRING_PROFILES_ACTIVE",
    "NODE_ENV",
    "PATH",
    "HOME",
    "PWD",
}


def check(tool_name, text, tool_input):
    if not text:
        return None

    if ENV_DUMP_RE.search(text):
        return (
            "deny",
            "Bare env/printenv/export dumps the whole process environment, which may "
            "include secrets. Blocked by secrets policy.",
        )
    if DOTENV_FILE_RE.search(text) or KEY_FILE_RE.search(text):
        return (
            "deny",
            "Reading .env* files or key/service-account files directly is blocked by "
            "secrets policy.",
        )
    if GCLOUD_SECRET_RE.search(text):
        return ("deny", "gcloud secrets access is blocked by secrets policy.")
    if GH_SECRET_RE.search(text):
        return ("deny", "gh secret commands are blocked by secrets policy.")
    if DOCKER_INSPECT_RE.search(text):
        return (
            "deny",
            "docker inspect / docker exec ... env can leak container secrets (see "
            "infra/deployment-plan.md accepted risks); blocked by secrets policy.",
        )
    if PROC_ENVIRON_RE.search(text):
        return ("deny", "Reading /proc/*/environ is blocked by secrets policy.")

    exempt_spans = [m.span() for m in DB_CLIENT_DSN_ARG_RE.finditer(text)]

    for match in SECRET_VAR_RE.finditer(text):
        name = match.group(1)
        if name in SAFE_VAR_NAMES:
            continue
        if not SECRET_NAME_RE.search(name):
            continue
        start, end = match.span()
        if any(exempt_start <= start and end <= exempt_end for exempt_start, exempt_end in exempt_spans):
            continue
        return (
            "deny",
            f"Reference to ${{{name}}} matches a secret-like variable name "
            "pattern; blocked by secrets policy.",
        )

    return None
