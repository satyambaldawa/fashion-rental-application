import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import secrets


class SecretsPolicyTest(unittest.TestCase):
    def test_denies_bare_printenv(self):
        result = secrets.check("Bash", "printenv", {})
        self.assertEqual(result[0], "deny")

    def test_denies_bare_env(self):
        result = secrets.check("Bash", "env", {})
        self.assertEqual(result[0], "deny")

    def test_denies_dotenv_file_read(self):
        result = secrets.check("Bash", "cat backend/.env", {})
        self.assertEqual(result[0], "deny")

    def test_denies_secret_named_var_echo(self):
        result = secrets.check("Bash", "echo $JWT_SECRET", {})
        self.assertEqual(result[0], "deny")

    def test_denies_database_url_var_echo(self):
        result = secrets.check("Bash", "echo ${SUPABASE_DATABASE_URL}", {})
        self.assertEqual(result[0], "deny")

    def test_denies_gcloud_secrets_access(self):
        result = secrets.check(
            "Bash", "gcloud secrets versions access latest --secret=jwt", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_gh_secret_list(self):
        result = secrets.check("Bash", "gh secret list", {})
        self.assertEqual(result[0], "deny")

    def test_denies_docker_inspect(self):
        result = secrets.check(
            "Bash", "docker inspect fashion-rental-backend", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_proc_environ_read(self):
        result = secrets.check("Bash", "cat /proc/1234/environ", {})
        self.assertEqual(result[0], "deny")

    def test_allows_nonsensitive_var_echo(self):
        result = secrets.check("Bash", "echo $VITE_API_URL", {})
        self.assertIsNone(result)

    def test_allows_unrelated_command(self):
        result = secrets.check("Bash", "pnpm test", {})
        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
