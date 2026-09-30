import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("realm_guard", ROOT / "scripts/check_keycloak_realm.py")
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class RealmGuardTest(unittest.TestCase):
    def setUp(self):
        self.template = json.loads((ROOT / "resources/keycloak/realm.json").read_text())

    def test_committed_template_and_seeded_users_are_allowed(self):
        self.assertEqual([], guard.validate_realms(self.template))
        self.assertEqual({"user", "depositor", "admin"},
                         {u["username"] for u in self.template[0]["users"]})

    def test_each_key_provider_rejects_exported_material(self):
        for field in ("privateKey", "certificate", "secret", "kid"):
            for index in range(4):
                with self.subTest(field=field, index=index):
                    data = copy.deepcopy(self.template)
                    data[0]["components"]["org.keycloak.keys.KeyProvider"][index]["config"][field] = ["sensitive-test-value"]
                    problems = guard.validate_realms(data)
                    self.assertTrue(problems)
                    self.assertNotIn("sensitive-test-value", str(problems))

    def test_literal_client_secret_is_rejected(self):
        client = next(c for c in self.template[0]["clients"] if c["clientId"] == "v2x-app-api")
        client["secret"] = "sensitive-test-value"
        self.assertTrue(guard.validate_realms(self.template))

    def test_additional_users_and_other_realms_do_not_get_credential_exemption(self):
        user = copy.deepcopy(self.template[0]["users"][0])
        user["username"] = "another-user"
        self.template[0]["users"].append(user)
        self.assertTrue(guard.validate_realms(self.template))
        self.template[0]["users"].pop()
        self.template[0]["realm"] = "another-realm"
        self.assertTrue(guard.validate_realms(self.template))

    def test_legacy_realm_keys_are_rejected(self):
        self.template[0]["privateKey"] = "sensitive-test-value"
        self.assertTrue(guard.validate_realms(self.template))

    def test_changed_credentials_for_seeded_users_are_rejected(self):
        self.template[0]["users"][0]["credentials"][0]["secretData"] = "sensitive-test-value"
        self.assertTrue(guard.validate_realms(self.template))

    def test_cli_fails_without_printing_values(self):
        self.template[0]["privateKey"] = "sensitive-test-value"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "realm.json"
            path.write_text(json.dumps(self.template))
            result = subprocess.run([sys.executable, str(ROOT / "scripts/check_keycloak_realm.py"), str(path)],
                                    capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn("/0/privateKey", result.stderr)
        self.assertNotIn("sensitive-test-value", result.stdout + result.stderr)

    def test_invalid_json_does_not_print_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "realm.json"
            path.write_text('sensitive-test-value')
            result = subprocess.run([sys.executable, str(ROOT / "scripts/check_keycloak_realm.py"), str(path)],
                                    capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertNotIn("sensitive-test-value", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
