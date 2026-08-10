import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import gcp


class GcpPolicyTest(unittest.TestCase):
    def test_denies_instance_delete(self):
        result = gcp.check(
            "Bash",
            "gcloud compute instances delete fashion-rental-backend "
            "--zone=us-central1-a --quiet",
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_instance_stop(self):
        result = gcp.check(
            "Bash",
            "gcloud compute instances stop fashion-rental-backend --zone=us-central1-a",
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_address_delete(self):
        result = gcp.check(
            "Bash",
            "gcloud compute addresses delete fashion-rental-ip --region=us-central1 --quiet",
            {},
        )
        self.assertEqual(result[0], "deny")

    def test_denies_project_delete(self):
        result = gcp.check("Bash", "gcloud projects delete fashion-rental-123456", {})
        self.assertEqual(result[0], "deny")

    def test_denies_docker_rm(self):
        result = gcp.check("Bash", "docker rm fashion-rental-backend", {})
        self.assertEqual(result[0], "deny")

    def test_denies_docker_volume_prune(self):
        result = gcp.check("Bash", "docker system prune -af --volumes", {})
        self.assertEqual(result[0], "deny")

    def test_allows_instance_describe(self):
        result = gcp.check(
            "Bash",
            "gcloud compute instances describe fashion-rental-backend --zone=us-central1-a",
            {},
        )
        self.assertEqual(result[0], "allow")

    def test_allows_docker_logs(self):
        result = gcp.check("Bash", "docker logs --tail 200 fashion-rental-backend", {})
        self.assertEqual(result[0], "allow")

    def test_allows_approved_container_restart(self):
        result = gcp.check("Bash", "docker restart fashion-rental-backend", {})
        self.assertEqual(result[0], "allow")

    def test_defers_unrelated_command(self):
        result = gcp.check("Bash", "pnpm build", {})
        self.assertIsNone(result)

    def test_denies_instance_create(self):
        result = gcp.check(
            "Bash", "gcloud compute instances create fashion-rental-backend --zone=us-central1-a", {}
        )
        self.assertEqual(result[0], "deny")

    def test_denies_firewall_rule_create(self):
        result = gcp.check(
            "Bash", "gcloud compute firewall-rules create allow-http --allow=tcp:80", {}
        )
        self.assertEqual(result[0], "deny")


if __name__ == "__main__":
    unittest.main()
