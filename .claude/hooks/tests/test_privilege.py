import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy import privilege


class PrivilegePolicyTest(unittest.TestCase):
    def test_asks_on_sudo(self):
        result = privilege.check("Bash", "sudo lsof -i:8080", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_su(self):
        result = privilege.check("Bash", "su - postgres", {})
        self.assertEqual(result[0], "ask")

    def test_does_not_match_su_as_substring(self):
        result = privilege.check("Bash", "svn status", {})
        self.assertIsNone(result)

    def test_asks_on_ssh_keygen(self):
        result = privilege.check("Bash", "ssh-keygen -t ed25519", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_gcloud_auth(self):
        result = privilege.check("Bash", "gcloud auth login", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_aws_configure(self):
        result = privilege.check("Bash", "aws configure", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_npm_login(self):
        result = privilege.check("Bash", "npm login", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_gpg_gen_key(self):
        result = privilege.check("Bash", "gpg --gen-key", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_crontab_edit(self):
        result = privilege.check("Bash", "crontab -e", {})
        self.assertEqual(result[0], "ask")

    def test_allows_crontab_list(self):
        result = privilege.check("Bash", "crontab -l", {})
        self.assertIsNone(result)

    def test_asks_on_systemctl_enable(self):
        result = privilege.check("Bash", "systemctl enable postgresql", {})
        self.assertEqual(result[0], "ask")

    def test_allows_systemctl_status(self):
        result = privilege.check("Bash", "systemctl status postgresql", {})
        self.assertIsNone(result)

    def test_asks_on_iptables(self):
        result = privilege.check("Bash", "iptables -L", {})
        self.assertEqual(result[0], "ask")

    def test_asks_on_ufw_allow(self):
        result = privilege.check("Bash", "ufw allow 8080", {})
        self.assertEqual(result[0], "ask")

    def test_ignores_unrelated_command(self):
        result = privilege.check("Bash", "pnpm test", {})
        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
