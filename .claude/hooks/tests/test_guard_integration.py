import json
import os
import subprocess
import sys
import unittest

GUARD_PATH = os.path.join(os.path.dirname(__file__), "..", "guard.py")
HOOKS_DIR = os.path.join(os.path.dirname(__file__), "..")
REPO_ROOT = os.path.join(HOOKS_DIR, "..", "..")
SETTINGS_PATH = os.path.join(HOOKS_DIR, "..", "settings.json")


def run_guard(tool_name, tool_input, raw_stdin=None, agent_type=None):
    payload = {"tool_name": tool_name, "tool_input": tool_input}
    if agent_type:
        payload["agent_type"] = agent_type
    stdin_text = raw_stdin if raw_stdin is not None else json.dumps(payload)
    result = subprocess.run(
        [sys.executable, GUARD_PATH],
        input=stdin_text,
        capture_output=True,
        text=True,
        timeout=10,
    )
    return result, json.loads(result.stdout) if result.stdout.strip() else {}


class GuardIntegrationTest(unittest.TestCase):
    def test_denies_secret_env_read(self):
        _, output = run_guard("Bash", {"command": "echo $JWT_SECRET"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_denies_gcp_instance_delete(self):
        _, output = run_guard(
            "Bash",
            {
                "command": "gcloud compute instances delete fashion-rental-backend "
                "--zone=us-central1-a --quiet"
            },
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_allows_gcp_container_restart(self):
        _, output = run_guard("Bash", {"command": "docker restart fashion-rental-backend"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_denies_db_drop_regardless_of_host(self):
        _, output = run_guard(
            "Bash", {"command": 'psql -h localhost -p 5433 -c "DROP TABLE items"'}
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_allows_db_select_against_any_host(self):
        _, output = run_guard(
            "Bash", {"command": 'psql "$SUPABASE_DATABASE_URL" -c "SELECT 1"'}
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_denies_gh_pr_merge(self):
        _, output = run_guard("Bash", {"command": "gh pr merge 42 --squash"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_allows_gh_ci_trigger(self):
        _, output = run_guard("Bash", {"command": "gh workflow run ci.yml"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_allows_unrelated_command_by_default(self):
        # default-allow-except-denied: an unmatched Bash command is no longer
        # deferred to settings.json's allow-list (which a background subagent
        # can never satisfy without an unanswerable prompt) — the checkers
        # above are the real gate, so nothing objecting means allow.
        _, output = run_guard("Bash", {"command": "pnpm test"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_allows_destructive_looking_text_inside_a_heredoc_body(self):
        # Regression: only pr_review.py used to strip heredoc bodies before
        # matching, so a script/doc merely *containing* a dangerous-looking
        # command as data (not an invoked command) tripped other policies too.
        dangerous_looking_line = "this mentions " + "rm -rf" + " /important/stuff as an example"
        _, output = run_guard(
            "Bash",
            {
                "command": "cat <<'EOF' > /tmp/notes.txt\n"
                + dangerous_looking_line
                + "\nEOF\necho done"
            },
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_asks_on_git_checkout(self):
        _, output = run_guard("Bash", {"command": "git checkout feature/other"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "ask")

    def test_asks_on_git_commit(self):
        # add/commit/push require an explicit ask from git_write.py now that
        # guard.py's Bash fallback is allow-by-default — leaving them
        # unmatched would have silently allowed them instead of gating them.
        _, output = run_guard("Bash", {"command": 'git commit -m "message"'})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "ask")

    def test_deny_still_wins_over_ask(self):
        # git reset --hard is both destructive.py's hard deny and git_write's
        # ask (plain "reset" matches git_write too) — deny must still win.
        _, output = run_guard("Bash", {"command": "git reset --hard HEAD~3"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_ask_wins_over_allow_on_chained_command(self):
        # An allow-listed gh command chained with a git-write command must
        # not launder the ask past guard.py, for the same reason deny wins.
        _, output = run_guard("Bash", {"command": "gh pr view 82 && git merge main"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "ask")

    def test_secrets_checked_before_destructive_on_conflicting_command(self):
        # A command that is both a secret leak AND references rm -rf should
        # still report the secrets reason, since secrets.check runs first.
        _, output = run_guard(
            "Bash", {"command": "echo $JWT_SECRET && rm -rf /tmp/whatever"}
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")
        self.assertIn("secrets policy", output["hookSpecificOutput"]["permissionDecisionReason"])

    def test_fails_closed_on_malformed_stdin(self):
        result, output = run_guard(None, None, raw_stdin="not valid json {{{")
        self.assertEqual(result.returncode, 0)
        decision = output["hookSpecificOutput"]["permissionDecision"]
        self.assertIn(decision, ("ask", "deny"))

    def test_emits_no_decision_on_empty_stdin(self):
        result, output = run_guard(None, None, raw_stdin="")
        self.assertEqual(result.returncode, 0)
        self.assertEqual(output, {})

    def test_emits_no_decision_for_ordinary_write(self):
        # Regression: emitting "defer" paused the call for an external host,
        # which silently killed subagents on every Edit/Write.
        result, output = run_guard(
            "Write", {"file_path": "backend/src/main/java/Foo.java", "content": "x"}
        )
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertEqual(output, {})

    def test_subagent_ask_becomes_deny(self):
        _, output = run_guard(
            "Bash", {"command": "git checkout main"}, agent_type="general-purpose"
        )
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_main_session_ask_stays_ask(self):
        _, output = run_guard("Bash", {"command": "git checkout main"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "ask")

    def test_subagent_allow_is_unaffected(self):
        _, output = run_guard("Bash", {"command": "pnpm test"}, agent_type="general-purpose")
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_main_session_is_enforced_without_a_funnel(self):
        # Skills carry know-how but no identity; the hook enforces on command text
        # for the plain main session (no agent_type) exactly as before.
        deny_cases = [
            "echo $JWT_SECRET",
            "rm -rf frontend/node_modules",
            'psql -h prod-db -c "DROP TABLE items"',
            "gh pr merge 82 --squash",
            "gcloud compute instances delete fashion-rental-backend --zone=us-central1-a --quiet",
        ]
        for cmd in deny_cases:
            _, output = run_guard("Bash", {"command": cmd})
            self.assertEqual(
                output["hookSpecificOutput"]["permissionDecision"], "deny", cmd
            )
        allow_cases = ["gh pr view 82", 'psql -h localhost -p 5433 -c "SELECT 1"']
        for cmd in allow_cases:
            _, output = run_guard("Bash", {"command": cmd})
            self.assertEqual(
                output["hookSpecificOutput"]["permissionDecision"], "allow", cmd
            )


class GuardCloudSessionInvocationTest(unittest.TestCase):
    """Regression coverage for #92: settings.json's hook command must resolve
    guard.py whether or not CLAUDE_PROJECT_DIR is set — cloud sessions never
    populate it, so a bare `${CLAUDE_PROJECT_DIR}/...` reference silently
    fails to execute and PreToolUse denies-by-default, blocking every hooked
    tool call (see #91)."""

    def _hook_commands(self):
        with open(SETTINGS_PATH) as f:
            settings = json.load(f)
        return [
            entry["hooks"][0]["command"]
            for entry in settings["hooks"]["PreToolUse"]
        ]

    def _run_hook_command(self, command, claude_project_dir):
        env = os.environ.copy()
        if claude_project_dir is None:
            env.pop("CLAUDE_PROJECT_DIR", None)
        else:
            env["CLAUDE_PROJECT_DIR"] = claude_project_dir
        payload = json.dumps(
            {"tool_name": "Write", "tool_input": {"file_path": "x", "content": "y"}}
        )
        return subprocess.run(
            ["bash", "-c", command],
            input=payload,
            capture_output=True,
            text=True,
            timeout=10,
            cwd=REPO_ROOT,
            env=env,
        )

    def test_all_four_matchers_use_the_same_command(self):
        commands = self._hook_commands()
        self.assertEqual(len(commands), 4)
        self.assertEqual(len(set(commands)), 1)

    def test_hook_resolves_with_claude_project_dir_set(self):
        command = self._hook_commands()[0]
        result = self._run_hook_command(command, claude_project_dir=REPO_ROOT)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("No such file or directory", result.stderr)

    def test_hook_resolves_with_claude_project_dir_unset(self):
        # The exact cloud-session condition: CLAUDE_PROJECT_DIR absent, cwd is
        # the repo root (confirmed via a live claude --cloud spike on #91).
        command = self._hook_commands()[0]
        result = self._run_hook_command(command, claude_project_dir=None)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("No such file or directory", result.stderr)

    def test_hook_resolves_with_claude_project_dir_empty(self):
        command = self._hook_commands()[0]
        result = self._run_hook_command(command, claude_project_dir="")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("No such file or directory", result.stderr)


class GuardCloudPipelineGitExemptionTest(unittest.TestCase):
    """Regression coverage for #96: a simulated cloud session (HOME=/root --
    the confirmed real shape, verified against a live routine run's own
    shell-snapshot path -- CLAUDE_PROJECT_DIR unset) must get
    add/checkout/commit/push auto-allowed by git_write.py -- but critically,
    github.py's separate deny for pushing to main must still win, end to end
    through guard.py's own dispatch (deny beats allow), not just in
    git_write.py's own unit tests."""

    def _run_in_simulated_cloud(self, command, home="/root"):
        env = {
            key: value
            for key, value in os.environ.items()
            if key not in ("CLAUDE_PROJECT_DIR",)
        }
        env["HOME"] = home
        payload = json.dumps({"tool_name": "Bash", "tool_input": {"command": command}})
        result = subprocess.run(
            [sys.executable, GUARD_PATH],
            input=payload,
            capture_output=True,
            text=True,
            timeout=10,
            env=env,
        )
        return json.loads(result.stdout) if result.stdout.strip() else {}

    def test_allows_checkout_when_home_is_root(self):
        output = self._run_in_simulated_cloud("git checkout -b feat/issue-99-example", home="/root")
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_allows_checkout_when_home_is_home_user(self):
        output = self._run_in_simulated_cloud("git checkout -b feat/issue-99-example", home="/home/user")
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_allows_feature_branch_push_in_simulated_cloud_session(self):
        output = self._run_in_simulated_cloud("git push -u origin feat/issue-99-example")
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "allow")

    def test_still_denies_push_to_main_in_simulated_cloud_session(self):
        output = self._run_in_simulated_cloud("git push origin main")
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_still_denies_bare_push_in_simulated_cloud_session(self):
        output = self._run_in_simulated_cloud("git push")
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_still_denies_reset_hard_in_simulated_cloud_session(self):
        output = self._run_in_simulated_cloud("git reset --hard HEAD~3")
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_still_asks_on_git_write_in_simulated_local_environment(self):
        # Explicitly force a local-shaped HOME rather than trusting the test
        # runner's own environment -- on a Linux dev machine or CI runner,
        # ambient HOME is also under /home/, which would silently flip this
        # assertion if left implicit.
        env = {key: value for key, value in os.environ.items() if key != "CLAUDE_PROJECT_DIR"}
        env["HOME"] = "/Users/testuser"
        env["CLAUDE_PROJECT_DIR"] = "/Users/testuser/some-repo"
        payload = json.dumps({"tool_name": "Bash", "tool_input": {"command": "git checkout -b feat/x"}})
        result = subprocess.run(
            [sys.executable, GUARD_PATH], input=payload, capture_output=True, text=True, timeout=10, env=env,
        )
        output = json.loads(result.stdout) if result.stdout.strip() else {}
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "ask")


if __name__ == "__main__":
    unittest.main()
