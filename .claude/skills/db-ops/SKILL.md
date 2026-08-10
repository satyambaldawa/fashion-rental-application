---
name: db-ops
description: Use when inspecting the fashion-rental Postgres schema, running read-only SELECT/EXPLAIN queries against local dev or prod, or running local-dev Flyway migrations. Covers how to connect and what is safe. Enforcement is the guard hook + scoped credentials, not this skill.
---

# Database ops

Operational know-how for talking to the fashion-rental Postgres database directly
via `psql` / `pg_dump` / Flyway. Schema and domain rules live in
`technical-architecture.md` and `fashion-rental-discovery.md` — read them before
answering non-trivial questions about what the data means.

**This skill is guidance, not a wall.** What is actually permitted is enforced by
`.claude/hooks/guard.py` (which blocks writes/DDL against non-local hosts, all
`DROP`/`TRUNCATE`/`ALTER`/`GRANT`/`REVOKE`, and secret reads on any host) and by the
scoped database role the session runs as. Do not attempt to bypass either, and do
not suggest the user bypass them.

## What you do

- Inspect schema against local dev: `psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c '\dt'` / `-c '\d <table>'`, or query `information_schema` directly.
- Run read-only `SELECT`/`EXPLAIN`/`SHOW` — these work against whichever database the connection URL points to (local dev or prod). Any write keyword in the same command is blocked by the hook regardless of host.
- Run local-dev Flyway migrations: `cd backend && ./gradlew flywayMigrate` — only ever against `localhost:5433`. Read the migration file(s) in `db/migration/` first and confirm they are additive/reviewed.
- Run local-dev `pg_dump` for inspection against `localhost:5433`.

## What is off-limits

`DROP`/`TRUNCATE`/`ALTER`/`GRANT`/`REVOKE` on any host; `INSERT`/`UPDATE`/`DELETE`/DDL
against anything other than `localhost:5433`; `pg_dump`/`pg_restore` against prod
(that is `db-backup.yml`'s job); reading any credential value. The hook enforces all
of this — treat it as the boundary, and report rather than work around it.

## How you report

State the query you ran, against which source, and the result. For schema questions,
cite the actual columns/constraints you found — do not guess from the entity classes.
