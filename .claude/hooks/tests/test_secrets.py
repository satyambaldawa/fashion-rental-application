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

    def test_allows_database_url_var_as_psql_argument(self):
        result = secrets.check(
            "Bash", 'psql "$SUPABASE_DATABASE_URL" -c "SELECT 1"', {}
        )
        self.assertIsNone(result)

    def test_still_denies_database_url_var_echo_even_near_psql_word(self):
        result = secrets.check(
            "Bash", 'echo "using psql with $SUPABASE_DATABASE_URL"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_leak_chained_after_legitimate_psql_invocation(self):
        result = secrets.check(
            "Bash", "psql -h localhost && echo $JWT_SECRET", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_used_as_sql_text_via_flag_style_psql(self):
        result = secrets.check(
            "Bash", 'psql -h localhost -c "SELECT $JWT_SECRET"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_via_command_substitution_inside_psql_arg(self):
        result = secrets.check(
            "Bash",
            'psql -h localhost -c "SELECT 1" ; psql -h localhost -c "SELECT $(echo $JWT_SECRET)"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_as_sql_text_in_second_chained_psql(self):
        result = secrets.check(
            "Bash", 'psql -h localhost && psql -h remote -c "SELECT $SECRET_TOKEN"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_used_as_pg_dump_output_filename(self):
        result = secrets.check(
            "Bash", "pg_dump -h localhost -f $JWT_SECRET.sql", {}
        )
        self.assertEqual(result[0], "deny")

    def test_allows_database_url_var_with_pg_dump(self):
        result = secrets.check(
            "Bash", 'pg_dump "$SUPABASE_DATABASE_URL" > backup.sql', {}
        )
        self.assertIsNone(result)

    def test_denies_secret_via_prose_adjacent_to_psql_word(self):
        result = secrets.check(
            "Bash", 'echo "run psql $JWT_SECRET later"', {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_via_psql_word_after_separator_but_not_invoked(self):
        result = secrets.check(
            "Bash", "cat file.txt; echo psql $JWT_SECRET", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_via_psql_word_as_argument_to_other_command(self):
        result = secrets.check(
            "Bash", "foo psql $JWT_SECRET", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_secret_on_own_line_after_bare_psql(self):
        result = secrets.check("Bash", "psql\n$JWT_SECRET", {})
        self.assertEqual(result[0], "deny")

    def test_denies_neon_pg_url_var_echo(self):
        result = secrets.check("Bash", "echo $NEON_PG_URL", {})
        self.assertEqual(result[0], "deny")


if __name__ == "__main__":
    unittest.main()
