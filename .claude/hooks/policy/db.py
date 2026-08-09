import re

_READ_KEYWORD_RE = re.compile(r"\b(WITH|SELECT|EXPLAIN|SHOW)\b", re.IGNORECASE)
_WRITE_KEYWORD_RE = re.compile(
    r"\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE)\b", re.IGNORECASE
)
_ALWAYS_DENY_RE = re.compile(r"\b(DROP|TRUNCATE|ALTER|GRANT|REVOKE)\b", re.IGNORECASE)
_LOCAL_HOST_RE = re.compile(r"(localhost|127\.0\.0\.1)(:5433)?")
_PG_BACKUP_TOOL_RE = re.compile(r"\b(pg_dump|pg_restore)\b")
_SQL_TOOL_RE = re.compile(r"\bpsql\b")
_FLYWAY_ALLOW_RE = re.compile(r"\./gradlew\s+flywayMigrate\b")
_FLYWAY_PROD_DENY_RE = re.compile(r"\./gradlew\s+flywayMigrate\b[^\n]*spring\.profiles\.active=prod")
_BOOTRUN_DEV_ALLOW_RE = re.compile(r"\./gradlew\s+bootRun\b[^\n]*spring\.profiles\.active=dev")


def check(tool_name, text, tool_input):
    if not text:
        return None

    if _PG_BACKUP_TOOL_RE.search(text):
        if _LOCAL_HOST_RE.search(text):
            return ("allow", "Local dev pg_dump/pg_restore is permitted.")
        return (
            "deny",
            "pg_dump/pg_restore against a non-local database is reserved for the "
            "existing db-backup.yml workflow, not an agent.",
        )

    if not _SQL_TOOL_RE.search(text) and "gradlew" not in text:
        return None

    if _ALWAYS_DENY_RE.search(text):
        return (
            "deny",
            "DROP/TRUNCATE/ALTER/GRANT/REVOKE are never permitted via an agent, on any host.",
        )

    if _FLYWAY_PROD_DENY_RE.search(text):
        return (
            "deny",
            "Flyway migrations against the prod profile are not permitted via an agent.",
        )

    if _FLYWAY_ALLOW_RE.search(text) or _BOOTRUN_DEV_ALLOW_RE.search(text):
        return ("allow", "Local dev Flyway migration is an approved write action.")

    if _WRITE_KEYWORD_RE.search(text):
        if not _LOCAL_HOST_RE.search(text):
            return (
                "deny",
                "Write/DDL statements are only permitted against localhost:5433 (local dev); "
                "no literal local host was found in this command.",
            )
        return None

    if _READ_KEYWORD_RE.search(text):
        return ("allow", "Read-only SQL query.")

    return None
