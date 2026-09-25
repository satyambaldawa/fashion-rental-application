import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import git_write


class GitWritePolicyTest(unittest.TestCase):
    def test_asks_on_checkout(self):
        result = git_write.check("Bash", "git checkout feature/other", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_plain_reset(self):
        result = git_write.check("Bash", "git reset HEAD~1", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_merge(self):
        result = git_write.check("Bash", "git merge feature/other", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_rebase(self):
        result = git_write.check("Bash", "git rebase main", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_stash(self):
        result = git_write.check("Bash", "git stash pop", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_tag(self):
        result = git_write.check("Bash", "git tag v1.2.3", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_remote(self):
        result = git_write.check("Bash", "git remote add upstream https://example.com/repo.git", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_dry_run_clean(self):
        # Not covered by destructive.py's hard deny (that only matches -f/--force),
        # but still a write-adjacent git command worth gating.
        result = git_write.check("Bash", "git clean -n", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_add(self):
        # Must ask (not defer): guard.py's Bash fallback is now allow-by-default,
        # so leaving add/commit/push unmatched would silently allow them.
        result = git_write.check("Bash", "git add backend/src/Foo.java", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_commit(self):
        result = git_write.check("Bash", 'git commit -m "message"', {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_push(self):
        result = git_write.check("Bash", "git push origin feature/foo", {})
        self.assertEqual(result[0], "ask")

    def test_ignores_read_only_commands(self):
        for cmd in ("git status", "git log --oneline -5", "git diff", "git branch -a"):
            result = git_write.check("Bash", cmd, {})
            self.assertIsNone(result, cmd)

    def test_ignores_read_only_listing_forms(self):
        for cmd in (
            "git tag",
            "git tag --list",
            "git tag -l 'v1.*'",
            "git remote",
            "git remote -v",
            "git stash list",
            "git worktree list",
        ):
            self.assertIsNone(git_write.check("Bash", cmd, {}), cmd)

    def test_still_asks_on_mutating_forms_of_listing_commands(self):
        for cmd in ("git tag -d v1.0", "git remote remove origin", "git stash drop", "git worktree add ../x"):
            self.assertEqual(git_write.check("Bash", cmd, {})[0], "ask", cmd)

    def test_read_only_segment_does_not_hide_chained_mutation(self):
        result = git_write.check("Bash", "git tag --list && git push origin main", {})
        self.assertEqual(result[0], "ask")

    def test_ignores_unrelated_command(self):
        result = git_write.check("Bash", "pnpm test", {})
        self.assertIsNone(result)


class GitWriteCloudPipelineExemptionTest(unittest.TestCase):
    """#96's carve-out: /work-issue-auto's own add/checkout/commit/push must not
    stall an unattended cloud session waiting for a human who isn't there. Every
    other mutating verb, and every non-cloud session, must keep asking exactly
    as before."""

    def test_allows_checkout_in_cloud_session(self):
        with patch.object(git_write.os.path, "expanduser", return_value="/home/user"), \
             patch.dict(git_write.os.environ, {}, clear=False):
            git_write.os.environ.pop("CLAUDE_PROJECT_DIR", None)
            result = git_write.check("Bash", "git checkout main && git pull && git checkout -b feat/issue-1", {})
        self.assertEqual(result[0], "allow")

    def test_allows_add_commit_push_chain_in_cloud_session(self):
        with patch.object(git_write.os.path, "expanduser", return_value="/home/user"), \
             patch.dict(git_write.os.environ, {}, clear=False):
            git_write.os.environ.pop("CLAUDE_PROJECT_DIR", None)
            result = git_write.check(
                "Bash", "git add -A && git commit -m msg && git push -u origin feat/issue-1", {}
            )
        self.assertEqual(result[0], "allow")

    def test_still_asks_when_home_is_cloud_shaped_but_project_dir_is_set(self):
        # Both signals must agree -- a single misleading signal must not flip
        # a real local session into the exempted path.
        with patch.object(git_write.os.path, "expanduser", return_value="/home/user"), \
             patch.dict(git_write.os.environ, {"CLAUDE_PROJECT_DIR": "/some/path"}, clear=False):
            result = git_write.check("Bash", "git checkout -b feat/x", {})
        self.assertEqual(result[0], "ask")

    def test_still_asks_when_project_dir_unset_but_home_is_local_shaped(self):
        with patch.object(git_write.os.path, "expanduser", return_value="/Users/satyambaldawa"), \
             patch.dict(git_write.os.environ, {}, clear=False):
            git_write.os.environ.pop("CLAUDE_PROJECT_DIR", None)
            result = git_write.check("Bash", "git checkout -b feat/x", {})
        self.assertEqual(result[0], "ask")

    def test_still_asks_on_unsafe_verb_even_in_cloud_session(self):
        with patch.object(git_write.os.path, "expanduser", return_value="/home/user"), \
             patch.dict(git_write.os.environ, {}, clear=False):
            git_write.os.environ.pop("CLAUDE_PROJECT_DIR", None)
            result = git_write.check("Bash", "git add -A && git reset --hard HEAD~1", {})
        self.assertEqual(result[0], "ask")

    def test_still_asks_on_merge_in_cloud_session(self):
        with patch.object(git_write.os.path, "expanduser", return_value="/home/user"), \
             patch.dict(git_write.os.environ, {}, clear=False):
            git_write.os.environ.pop("CLAUDE_PROJECT_DIR", None)
            result = git_write.check("Bash", "git merge main", {})
        self.assertEqual(result[0], "ask")


if __name__ == "__main__":
    unittest.main()
