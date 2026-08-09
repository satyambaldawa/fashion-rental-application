import json
import os
import subprocess
import sys
import unittest

GUARD_PATH = os.path.join(os.path.dirname(__file__), "..", "guard.py")


def run_guard(tool_name, tool_input, raw_stdin=None):
    stdin_text = (
        raw_stdin
        if raw_stdin is not None
        else json.dumps({"tool_name": tool_name, "tool_input": tool_input})
    )
    result = subprocess.run(
        [sys.executable, GUARD_PATH],
        input=stdin_text,
        capture_output=True,
        text=True,
        timeout=10,
    )
    return result, json.loads(result.stdout)


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

    def test_defers_unrelated_command(self):
        _, output = run_guard("Bash", {"command": "pnpm test"})
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "defer")

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

    def test_fails_closed_on_empty_stdin(self):
        result, output = run_guard(None, None, raw_stdin="")
        self.assertEqual(result.returncode, 0)
        self.assertEqual(output["hookSpecificOutput"]["permissionDecision"], "defer")


if __name__ == "__main__":
    unittest.main()
