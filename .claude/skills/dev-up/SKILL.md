---
name: dev-up
description: Use when the user wants to start, restart, or resume local development on this repo — bringing up Postgres, the backend (dev profile), and the frontend dev server, or when a dev server is stuck/unresponsive on its port and needs to be freed and restarted.
---

# Dev Up

One-shot local environment bootstrap. Collapses the manual lsof/pkill/podman/curl
dance (the single most repeated sequence across past sessions on this repo) into one
idempotent command: check what's already healthy, only touch what isn't.

## Arguments

`/dev-up [backend|frontend] [restart]` — no args means "ensure everything (Postgres,
backend, frontend) is up." Scope to one service with `backend`/`frontend`. Add `restart`
to force a kill-and-restart of the in-scope service(s) even if already healthy (e.g. to
pick up a config change that hot-reload won't catch).

## Workflow

**1. Check current health first — never restart something that's already fine.**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health              # backend
curl -s -o /dev/null -w "%{http_code}" http://localhost:5173                              # frontend
podman exec fashion-rental-application-postgres-1 pg_isready -U postgres 2>/dev/null      # postgres
```
Skip straight to step 5 for anything already healthy, unless `restart` was requested.
Note: `pg_isready`/`psql` are generally not installed on the host here — always check
readiness and query the DB *inside* the container via `podman exec`, never assume a host
binary exists.

**2. Postgres.** If the `podman exec ... pg_isready` check fails:
```bash
podman machine list   # confirm the podman VM is running; `podman machine start` if not
podman start fashion-rental-application-postgres-1   # if the container already exists (exited)
docker-compose up -d                                 # only if it doesn't exist yet at all
```
Try `podman start` on the named container first — confirmed live that `docker-compose up
-d` is **not** idempotent in this podman setup: if the container already exists (even
stopped), compose tries to *create* a new one with the same name and fails with "name
already in use" instead of starting it. Only fall back to `docker-compose up -d` when
`podman start` itself reports no such container.

Poll `podman exec fashion-rental-application-postgres-1 pg_isready -U postgres` every
second for up to ~20s. Container name is `fashion-rental-application-postgres-1` (from
`docker-compose.yml`); credentials are `postgres`/`postgres`, db `fashionrental` —
confirmed against the actual compose file and past `psql` usage. This is what CLAUDE.md's
example env-var block gets wrong (it documents `fashion_user`/`fashion_rental`); trust
the compose file over that doc block.

**3. Backend** (skip if out of scope). If not healthy, or `restart` requested:
```bash
lsof -ti tcp:8080   # find a PID bound to the port, if any
```
If a PID is found: it's a stale/stuck server (or the current one, under `restart`) —
`kill <pid>`, wait ~2s, `kill -9 <pid>` only if it's still alive. Then start fresh:
```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```
Run this with `run_in_background: true` — it never exits on its own. Immediately follow
with a second background command to get exactly one ready notification instead of
polling yourself:
```bash
until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do sleep 1; done; echo "backend healthy"
```
Also `run_in_background: true`. Gradle+Spring Boot cold start can take 30-60s; don't
treat silence as failure before then.

Once healthy, HTTP 200 already proves more than it looks like: this repo runs
Hibernate `ddl-auto: validate`, so the app refuses to start at all if Flyway's schema
doesn't match the entities. A 200 already means migrations ran and the schema is
correct — no separate schema check needed. It does *not* mean there's any actual data,
so check that directly:
```bash
podman exec fashion-rental-application-postgres-1 psql -U postgres -d fashionrental -tAc "select count(*) from items"
```
Report the row count as part of backend status (e.g. "backend healthy, 14 items in DB")
rather than just "backend healthy" — an empty table is a real signal worth surfacing,
not a failure to hide.

**4. Frontend** (skip if out of scope). Same shape:
```bash
lsof -ti tcp:5173   # kill if found (stale or restart)
cd frontend && pnpm dev                                                        # run_in_background: true
until curl -sf http://localhost:5173 >/dev/null 2>&1; do sleep 1; done; echo "frontend healthy"   # run_in_background: true
```

**5. Report.** For each service, say whether it was already up, freshly started, or
restarted. Give the URLs (`http://localhost:8080`, `http://localhost:8080/swagger-ui.html`,
`http://localhost:5173`) and the background task IDs for anything you started, so the
user can `Read` their output or `TaskStop` them later. Include the DB row count from
step 3.

**6. Ask before going further — never launch a browser unprompted.** Health + DB checks
are enough to call the stack "up." Whether the frontend actually *renders* that data is
a separate, heavier question — ask the user (e.g. via AskUserQuestion, yes/no) whether to
verify it with Playwright. Only proceed to step 7 on an explicit yes; a plain "looks
good" report is a complete answer on its own if they decline or don't respond.

**7. Playwright verification** (only after explicit yes). Use the
`mcp__plugin_playwright_playwright__*` tools (`ToolSearch` for their schemas if not yet
loaded — do not use `claude-in-chrome` here, the user wants Playwright specifically):
1. `browser_navigate` to `http://localhost:5173/login`.
2. `browser_snapshot` to get refs, then fill `Username`/`Password` with the dev profile's
   seeded login (`admin`/`admin` per `application-dev.yml`) and click **Sign In**.
3. `browser_navigate` to `http://localhost:5173/inventory` (an `OwnerRoute` page that
   renders real rows — the login above must succeed as an OWNER for this to load rather
   than redirect to `/unauthorized`).
4. `browser_snapshot` again. Cross-check against the step-3 DB count: if the count was 0,
   expect the "No items found" empty state; if it was > 0, expect that text absent and
   item content present. Either direction mismatching the DB count is a real bug (stale
   frontend cache, broken query, wrong API URL) — report it as such, don't paper over it.
5. `browser_close` when done — don't leave a browser session dangling.

## Stopping

There's no `/dev-down` — stop what you started with `TaskStop <task_id>` from this
session's own IDs, or from a fresh session: `lsof -ti tcp:8080 | xargs kill` /
`lsof -ti tcp:5173 | xargs kill`. Leave Postgres running; there's no reason to tear
down the container between sessions.

## Hard rules

- Never kill a PID on 8080/5173 without checking it first via `lsof` — killing blind
  risks taking down an unrelated process that happens to be on that port.
- Never touch whatever's listening on 5433 directly (no `pkill`/`kill` against
  Postgres) — bring it up via `podman start fashion-rental-application-postgres-1`
  (falling back to `docker-compose up -d` only if that container doesn't exist).
- Don't declare a service "up" from process-start alone — always confirm via the
  health poll before reporting success, and for the backend, don't call it "up"
  without also reporting the DB row count from step 3.
- Never launch Playwright without an explicit yes from the user for that specific run.

## Common mistakes

- Running `./gradlew bootRun` or `pnpm dev` in the foreground — they never return,
  so the skill (and the session) hangs. Always `run_in_background: true`.
- Sleep-looping in the main conversation to wait for health. Use the `until ... ; do
  sleep 1; done` pattern as its own backgrounded command instead — one notification
  when it's ready, no polling from you.
- Restarting Postgres to "fix" a backend connection issue — the backend/Flyway are
  almost always the actual failure point; Postgres being briefly unreachable at
  container startup is normal and self-resolves within the poll window.
- Treating a backend 200 as proof the schema is stale/wrong and re-running migrations
  "just in case" — `ddl-auto: validate` already guarantees a 200 means the schema is
  correct. The thing actually worth checking is data, via the row-count query.
- Assuming Playwright is wanted because the user asked for it once — the browser step
  is per-run, gated on asking, every time this skill runs.
