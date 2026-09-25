## GitHub operations

Use `gh api` (REST) for every operation below. `gh` is not installed in the cloud
image by default — install it via Ubuntu's `universe` apt repo, not
`cli.github.com` directly (that host is blocked by the cloud environment's egress
policy):

```bash
apt-get update
apt-get install -y gh
```

Do not use raw `curl` with an `Authorization: Bearer $GH_TOKEN` header — this
repo's `guard.py` secrets policy denies any command that references a
`*_TOKEN`-shaped variable by name, `curl` included. `gh api` avoids this because
`gh` resolves credentials internally without the variable ever appearing in
command text.

`gh auth status` is **not a reliable readiness check** in this environment — it
can report the token as invalid while actual `gh api` calls succeed anyway (real
requests are authenticated by the cloud environment's proxy layer, separately
from `gh`'s own local credential check). Judge success by whether the API call
itself returns data, not by `gh auth status`.

### Access model (routine vs. one-off session — important)

All six operations below were verified live via a **scheduled routine**
(`RemoteTrigger`/`/schedule`), not a one-off `claude --cloud "<prompt>"` session.
That distinction matters:

- A one-off `claude --cloud` session repeatedly failed with "GitHub access to
  this repository is not enabled for this session" (referencing a nonexistent
  `add_repo` tool) even with the Claude GitHub App correctly installed on this
  exact repo *and* `/web-setup` run locally. This matches a known upstream bug:
  `anthropics/claude-code#84581`.
- A routine, with the same App/`/web-setup` configuration already in place, got
  working GitHub API access with no extra setup — reads and writes both
  succeeded on the first attempt.

Since `/work-issue-auto` (#94) will run as part of an hourly **routine** (#95),
this is good news: no further access configuration should be needed beyond what
is already set up. But this also means `/work-issue-auto` must never be
validated via a one-off `claude --cloud` session — it will fail for reasons
unrelated to the pipeline itself. Always test via a routine.

Separately: a routine-triggered session was **not** affected by the
`CLAUDE_PROJECT_DIR`-unset hook bug (#91/#92) that blocks one-off `--cloud`
sessions — `Bash`/`Edit`/`Write` worked normally even against `main` before
#92's fix. #92's fix is still correct and worth keeping (it costs nothing and
covers one-off sessions too), but is evidently not required for the routine path
specifically.

### Read an issue

```bash
gh api repos/satyambaldawa/fashion-rental-application/issues/<N>
```
Verified live 2026-09-25 against issue #143.

### Add a label

```bash
gh api repos/satyambaldawa/fashion-rental-application/issues/<N>/labels \
  -X POST -f "labels[]=<label>"
```
Verified live 2026-09-25.

### Remove a label

```bash
gh api repos/satyambaldawa/fashion-rental-application/issues/<N>/labels/<label> \
  -X DELETE
```

`guard.py`'s GitHub policy denies any `gh api ... -X DELETE` by default (it
guards against deleting the repo or branch protection via raw REST calls). This
exact shape — `DELETE` against `/issues/<N>/labels/<name>` — is explicitly
exempted (see `policy/github.py`, `_SAFE_LABEL_DELETE_RE`); no other DELETE form
is. Confirmed live 2026-09-25 that this command is blocked on the unpatched
`main` branch, exactly as expected — the exemption is unit-tested
(`test_github.py`) but not yet re-confirmed live post-merge, since a routine
cannot target a non-default branch. Re-verify once this fix lands on `main`.

### Post a comment

```bash
gh api repos/satyambaldawa/fashion-rental-application/issues/<N>/comments \
  -X POST -f body="<text>"
```
Verified live 2026-09-25.

### Resolve when a label was applied (for the staleness watchdog)

```bash
gh api repos/satyambaldawa/fashion-rental-application/issues/<N>/timeline --paginate
```
Take the most recent `labeled` event whose `label.name` is `auto-in-progress`.
Verified live 2026-09-25 (paginated call succeeded; scratch issue had no
`labeled` events at the time, so the filter itself is untested against real
label-event data — re-check once #94 exercises the full label lifecycle).

### Open a pull request

```bash
gh api repos/satyambaldawa/fashion-rental-application/pulls \
  -X POST -f title="<title>" -f head="<branch>" -f base="main" -f body="<body>"
```

Not fired live — deliberately. Per CLAUDE.md and the `github-ops` skill, PR
creation requires the user's explicit approval; every attempt to get an
unattended session to run it (one-off and routine both) correctly declined or
was withheld pending approval that no one was present to give. Mechanically this
is the same `gh api ... -X POST` form already proven to work for labels and
comments, so there is no reason to expect it behaves differently — but
`/work-issue-auto`'s own operating rules (#94) must establish that this specific,
pre-scoped automation is the standing authorization, or the pipeline can never
open a PR unattended. This is the concrete thing #96 (scoped CLAUDE.md carve-out)
needs to resolve.
