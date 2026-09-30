#!/usr/bin/env python3
"""Reject secrets in realm templates without displaying their values.

The three documented local-development users are intentionally retained.
Client secrets must reference KEYCLOAK_CLIENT_SECRET at import time.
"""

import argparse
import hashlib
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
# SHA-256 of canonical credential JSON, not new credentials or password defaults.
# Only the existing documented demo records are exempt from the credential guard.
DEVELOPMENT_CREDENTIALS = {
    "user": "afb14e1b5ec6a2d7ce99d834d332821208848dc6320c5bdbd058b8e49662bb24",
    "admin": "c8c3b691d0a6e510b20b36bd8fd2f2b3996ef5260fbc279b816d0a3640d718db",
    "depositor": "d8c775ec79cc37926aa0d3e0ca7ab3dc681736a358a9f58923ac14128c91d620",
}
KEY_MATERIAL_FIELDS = {"privatekey", "private_key", "certificate", "secret", "kid"}


def validate_realms(data):
    """Return JSON locations and reasons, never the corresponding values."""
    problems = []
    realms = data if isinstance(data, list) else [data]
    if not realms:
        return [("/", "empty realm template")]

    def inspect_keys(value, path):
        if isinstance(value, dict):
            for field, child in value.items():
                location = f"{path}/{field}"
                if field.lower() in KEY_MATERIAL_FIELDS:
                    problems.append((location, "exported key material or identifier"))
                inspect_keys(child, location)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                inspect_keys(child, f"{path}/{index}")

    for index, realm in enumerate(realms):
        prefix = f"/{index}"
        if not isinstance(realm, dict) or not realm.get("realm"):
            problems.append((prefix, "expected a realm object"))
            continue
        # Legacy exports can also carry key material at realm level.
        for field in ("privateKey", "publicKey", "certificate", "codeSecret"):
            if field in realm:
                problems.append((f"{prefix}/{field}", "exported realm key material"))
        inspect_keys(realm.get("components", {}).get("org.keycloak.keys.KeyProvider", []),
                     f"{prefix}/components/org.keycloak.keys.KeyProvider")
        for client_index, client in enumerate(realm.get("clients", [])):
            if "secret" in client and client["secret"] != "${KEYCLOAK_CLIENT_SECRET}":
                problems.append((f"{prefix}/clients/{client_index}/secret",
                                 "client secret must reference KEYCLOAK_CLIENT_SECRET"))
        for user_index, user in enumerate(realm.get("users", [])):
            credentials = user.get("credentials")
            if not credentials:
                continue
            fingerprint = hashlib.sha256(json.dumps(credentials, sort_keys=True,
                                                     separators=(",", ":")).encode()).hexdigest()
            allowed = (realm["realm"] == "v2x-app"
                       and DEVELOPMENT_CREDENTIALS.get(user.get("username")) == fingerprint)
            if not allowed:
                problems.append((f"{prefix}/users/{user_index}/credentials",
                                 "only existing documented development credentials are allowed"))
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path,
                        help="Realm JSON files; defaults to resources/keycloak/**/*.json")
    args = parser.parse_args()
    paths = args.paths or sorted((ROOT / "resources/keycloak").rglob("*.json"))
    if not paths:
        print("No realm templates found", file=sys.stderr)
        return 1
    failed = False
    for path in paths:
        try:
            problems = validate_realms(json.loads(path.read_text()))
        except (OSError, ValueError, TypeError, AttributeError):
            # Do not include parser exceptions: they can contain secret input.
            problems = [("/", "unreadable or invalid realm template")]
        for location, reason in problems:
            print(f"{path}:{location}: {reason}", file=sys.stderr)
            failed = True
    if not failed:
        print(f"Validated {len(paths)} Keycloak realm template(s)")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
