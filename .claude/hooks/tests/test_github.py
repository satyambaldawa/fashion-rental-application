import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import github


class GithubPolicyTest(unittest.TestCase):
    def test_denies_repo_delete(self):
        result = github.check("Bash", "gh repo delete satyambaldawa/fashion-rental --yes", {})
        self.assertEqual(result[0], "deny")

    def test_denies_pr_merge(self):
        result = github.check("Bash", "gh pr merge 42 --squash", {})
        self.assertEqual(result[0], "deny")

    def test_denies_pr_approve(self):
        result = github.check("Bash", "gh pr review 42 --approve", {})
        self.assertEqual(result[0], "deny")

    def test_denies_cd_workflow_trigger(self):
        result = github.check("Bash", "gh workflow run cd.yml", {})
        self.assertEqual(result[0], "deny")

    def test_denies_infra_provision_trigger(self):
        result = github.check(
            "Bash", "gh workflow run infra-provision.yml -f action=destroy", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_direct_push_to_main(self):
        result = github.check("Bash", "git push origin main", {})
        self.assertEqual(result[0], "deny")

    def test_defers_branch_protection_put_change(self):
        result = github.check(
            "Bash", "gh api -X PUT repos/o/r/branches/main/protection", {}
        )
        self.assertIsNone(result)  # PUT not covered; see next test for DELETE

    def test_denies_branch_protection_delete(self):
        result = github.check(
            "Bash", "gh api -X DELETE repos/o/r/branches/main/protection", {}
        )
        self.assertEqual(result[0], "deny")

    def test_allows_issue_comment(self):
        result = github.check("Bash", 'gh issue comment 7 --body "update"', {})
        self.assertEqual(result[0], "allow")

    def test_allows_ci_workflow_trigger(self):
        result = github.check("Bash", "gh workflow run ci.yml", {})
        self.assertEqual(result[0], "allow")

    def test_allows_pr_view(self):
        result = github.check("Bash", "gh pr view 42", {})
        self.assertEqual(result[0], "allow")

    def test_allows_push_to_feature_branch(self):
        result = github.check("Bash", "git push origin feature/agent-safety-net", {})
        self.assertIsNone(result)

    def test_defers_unrelated_command(self):
        result = github.check("Bash", "pnpm lint", {})
        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
