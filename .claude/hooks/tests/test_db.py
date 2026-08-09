import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import db


class DbPolicyTest(unittest.TestCase):
    def test_always_denies_drop_regardless_of_host(self):
        result = db.check(
            "Bash",
            'psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c "DROP TABLE items"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_write_against_non_local_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "UPDATE items SET status=1"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_allows_select_via_psql_against_any_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "SELECT count(*) FROM items"',
            {},
        )
        self.assertEqual(result[0], "allow")

    def test_allows_flyway_migrate(self):
        result = db.check("Bash", "cd backend && ./gradlew flywayMigrate", {})
        self.assertEqual(result[0], "allow")

    def test_allows_local_pg_dump(self):
        result = db.check(
            "Bash",
            "pg_dump -h localhost -p 5433 -U fashion_user fashion_rental > backup.sql",
            {},
        )
        self.assertEqual(result[0], "allow")

    def test_denies_pg_dump_against_non_local_host(self):
        result = db.check("Bash", 'pg_dump "$NEON_PG_URL" | gzip > backup.sql.gz', {})
        self.assertEqual(result[0], "deny")

    def test_defers_unrelated_command(self):
        result = db.check("Bash", "pnpm test", {})
        self.assertIsNone(result)

    def test_defers_psql_with_no_recognizable_sql(self):
        result = db.check("Bash", "psql --version", {})
        self.assertIsNone(result)

    def test_defers_bare_interactive_psql_against_remote_host(self):
        result = db.check(
            "Bash", 'psql "$SUPABASE_DATABASE_URL"', {}
        )
        self.assertIsNone(result)

    def test_denies_insert_select_against_non_local_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "INSERT INTO items SELECT * FROM staging_items"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_delete_with_subselect_against_non_local_host(self):
        result = db.check(
            "Bash",
            'psql "$SUPABASE_DATABASE_URL" -c "DELETE FROM items WHERE id IN (SELECT id FROM stale)"',
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_flyway_migrate_against_prod_profile(self):
        result = db.check(
            "Bash", "./gradlew flywayMigrate -Dspring.profiles.active=prod", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_pg_restore_against_non_local_host(self):
        result = db.check(
            "Bash", "pg_restore -h prod-db.supabase.co -d fashion_rental backup.dump", {}
        )
        self.assertEqual(result[0], "deny")

    def test_allows_local_pg_restore(self):
        result = db.check(
            "Bash", "pg_restore -h localhost -p 5433 -d fashion_rental backup.dump", {}
        )
        self.assertEqual(result[0], "allow")


if __name__ == "__main__":
    unittest.main()
