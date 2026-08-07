import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import destructive


class DestructivePolicyTest(unittest.TestCase):
    def test_denies_rm_rf_combined_flag(self):
        result = destructive.check("Bash", "rm -rf /opt/app/old", {})
        self.assertEqual(result[0], "deny")

    def test_denies_rm_separated_flags(self):
        result = destructive.check("Bash", "rm -r -f ./build", {})
        self.assertEqual(result[0], "deny")

    def test_allows_rm_force_only(self):
        result = destructive.check("Bash", "rm -f package-lock.json", {})
        self.assertIsNone(result)

    def test_denies_git_push_force(self):
        result = destructive.check("Bash", "git push --force origin main", {})
        self.assertEqual(result[0], "deny")

    def test_denies_git_push_short_force_flag(self):
        result = destructive.check("Bash", "git push -f origin main", {})
        self.assertEqual(result[0], "deny")

    def test_allows_plain_git_push(self):
        result = destructive.check("Bash", "git push origin feature/foo", {})
        self.assertIsNone(result)

    def test_denies_git_reset_hard(self):
        result = destructive.check("Bash", "git reset --hard HEAD~3", {})
        self.assertEqual(result[0], "deny")

    def test_denies_git_clean_force(self):
        result = destructive.check("Bash", "git clean -fd", {})
        self.assertEqual(result[0], "deny")

    def test_denies_git_branch_force_delete(self):
        result = destructive.check("Bash", "git branch -D old-feature", {})
        self.assertEqual(result[0], "deny")

    def test_denies_shutdown(self):
        result = destructive.check("Bash", "sudo shutdown -h now", {})
        self.assertEqual(result[0], "deny")

    def test_allows_unrelated_command(self):
        result = destructive.check("Bash", "git status", {})
        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
