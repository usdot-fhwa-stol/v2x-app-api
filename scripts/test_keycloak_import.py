#!/usr/bin/env python3
"""Test fresh imports, persistence, and key rotation in disposable Docker services.

Requires Docker, Python 3, and OpenSSL. No existing Compose services or volumes
are touched. All tokens and private key material stay out of test output.
"""

import argparse
import base64
import hashlib
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[1]


def docker(*args):
    result = subprocess.run(["docker", *args], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"Docker {args[0]} failed (exit {result.returncode})")
    return result.stdout.strip()


def request(base, path, token=None, form=None, body=None, method=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=10) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def wait_ready(base):
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            request(base, "/realms/v2x-app/.well-known/openid-configuration")
            return
        except (urllib.error.URLError, OSError):
            time.sleep(2)
    raise RuntimeError("Keycloak did not become ready within 180 seconds")


def decode_segment(value):
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def thumbprints(jwks):
    result = set()
    for key in jwks["keys"]:
        if key["kty"] == "RSA":
            public = {field: key[field] for field in ("e", "kty", "n")}
            digest = hashlib.sha256(json.dumps(public, sort_keys=True, separators=(",", ":")).encode()).digest()
            result.add(base64.urlsafe_b64encode(digest).decode().rstrip("="))
    return result


def verifies(token, jwks):
    """Check a lab token's RS256 signature against currently published keys."""
    header, payload, signature = token.split(".")
    metadata = json.loads(decode_segment(header))
    if metadata.get("alg") != "RS256":
        return False
    for key in jwks["keys"]:
        if key.get("kid") != metadata.get("kid") or key.get("use") != "sig":
            continue
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            cert = subprocess.run(["openssl", "x509", "-inform", "DER", "-pubkey", "-noout"],
                                  input=base64.b64decode(key["x5c"][0]), capture_output=True)
            if cert.returncode:
                raise RuntimeError("Unable to read generated public certificate")
            (path / "public.pem").write_bytes(cert.stdout)
            (path / "signature").write_bytes(decode_segment(signature))
            verification = subprocess.run(["openssl", "dgst", "-sha256", "-verify", str(path / "public.pem"),
                                           "-signature", str(path / "signature")],
                                          input=f"{header}.{payload}".encode(), capture_output=True)
            return verification.returncode == 0
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", help="Use an already built shipped Keycloak image")
    args = parser.parse_args()
    prefix = "v2x-key-test-" + uuid.uuid4().hex[:10]
    image = args.image or prefix + ":test"
    containers = []
    network_created = False
    built_image = False
    password = secrets.token_urlsafe(24)
    client_secret = "change_this_for_production_use"
    historical = {key["sha256_jwk_thumbprint"] for key in
                  json.loads((ROOT / "scripts/tests/compromised-keycloak-public-keys.json").read_text())}
    try:
        if not args.image:
            print("Building shipped Keycloak image", flush=True)
            docker("build", "--tag", image, str(ROOT / "resources/keycloak"))
            built_image = True
        docker("network", "create", prefix)
        network_created = True
        installations = []
        for index in range(2):
            print(f"Starting isolated installation {index + 1}", flush=True)
            pg = f"{prefix}-pg-{index}"
            kc = f"{prefix}-kc-{index}"
            containers.append(pg)
            docker("run", "--detach", "--name", pg, "--network", prefix,
                   "--env", f"POSTGRES_PASSWORD={password}", "postgres:17-alpine")
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                ready = subprocess.run(["docker", "exec", pg, "pg_isready", "-U", "postgres"], capture_output=True)
                if ready.returncode == 0:
                    break
                time.sleep(1)
            else:
                raise RuntimeError("PostgreSQL did not become ready")
            containers.append(kc)
            docker("run", "--detach", "--name", kc, "--network", prefix,
                   "--publish", "127.0.0.1::8080",
                   "--env", "KC_DB=postgres", "--env", f"KC_DB_URL=jdbc:postgresql://{pg}:5432/postgres",
                   "--env", "KC_DB_USERNAME=postgres", "--env", f"KC_DB_PASSWORD={password}",
                   "--env", "KC_BOOTSTRAP_ADMIN_USERNAME=bootstrap-admin",
                   "--env", f"KC_BOOTSTRAP_ADMIN_PASSWORD={password}",
                   "--env", f"KEYCLOAK_CLIENT_SECRET={client_secret}",
                   image, "start-dev", "--import-realm", "--log-level=WARN")
            port = docker("port", kc, "8080/tcp").rsplit(":", 1)[1]
            base = f"http://127.0.0.1:{port}"
            wait_ready(base)
            jwks = request(base, "/realms/v2x-app/protocol/openid-connect/certs")
            fingerprints = thumbprints(jwks)
            assert len(fingerprints) == 2, "Expected generated RSA signing and encryption keys"
            assert not fingerprints & historical, "An exposed historical RSA key was imported"
            for username, role in (("user", "ROLE_USER"), ("depositor", "ROLE_DEPOSITOR"), ("admin", "ROLE_ADMIN")):
                token = request(base, "/realms/v2x-app/protocol/openid-connect/token", form={
                    "grant_type": "password", "client_id": "v2x-app-api", "client_secret": client_secret,
                    "username": username, "password": "12345"})["access_token"]
                claims = json.loads(decode_segment(token.split(".")[1]))
                assert role in claims["realm_access"]["roles"], "Seeded user lost its role"
                assert verifies(token, jwks), "Seeded user's token signature is invalid"
            admin_token = request(base, "/realms/master/protocol/openid-connect/token", form={
                "grant_type": "password", "client_id": "admin-cli",
                "username": "bootstrap-admin", "password": password})["access_token"]
            keys = request(base, "/admin/realms/v2x-app/keys", token=admin_token)
            assert {"RS256", "RSA-OAEP", "HS512", "AES"} <= {k["algorithm"] for k in keys["keys"]}, \
                "One of the four providers did not generate keys"
            installations.append((kc, base, jwks, token, keys))
        assert not thumbprints(installations[0][2]) & thumbprints(installations[1][2]), "Installations share RSA keys"
        print("Independent keys and seeded-user authentication verified", flush=True)

        kc, base, before, old_token, original_keys = installations[0]
        docker("restart", kc)
        # Docker may allocate a different host port for an ephemeral binding on restart.
        port = docker("port", kc, "8080/tcp").rsplit(":", 1)[1]
        base = f"http://127.0.0.1:{port}"
        wait_ready(base)
        after_restart = request(base, "/realms/v2x-app/protocol/openid-connect/certs")
        assert thumbprints(before) == thumbprints(after_restart), "Database-backed keys changed on restart"
        assert verifies(old_token, after_restart), "Existing token failed after a normal restart"
        print("Database-backed key persistence verified", flush=True)

        admin_token = request(base, "/realms/master/protocol/openid-connect/token", form={
            "grant_type": "password", "client_id": "admin-cli",
            "username": "bootstrap-admin", "password": password})["access_token"]
        providers = request(base, "/admin/realms/v2x-app/components?type=org.keycloak.keys.KeyProvider", token=admin_token)
        template = json.loads((ROOT / "resources/keycloak/realm.json").read_text())[0]
        realm_id = request(base, "/admin/realms/v2x-app", token=admin_token)["id"]
        for provider in template["components"]["org.keycloak.keys.KeyProvider"]:
            request(base, "/admin/realms/v2x-app/components", token=admin_token, body={
                "name": "replacement-" + provider["name"], "parentId": realm_id,
                "providerId": provider["providerId"], "providerType": "org.keycloak.keys.KeyProvider",
                "config": {**provider["config"], "priority": ["200"]}})
        for provider in providers:
            request(base, "/admin/realms/v2x-app/components/" + provider["id"], token=admin_token, method="DELETE")
        request(base, "/admin/realms/v2x-app/logout-all", token=admin_token, method="POST")
        offline_users = request(base, "/admin/realms/v2x-app/users", token=admin_token)
        for user in offline_users:
            request(base, "/admin/realms/v2x-app/users/" + user["id"] + "/logout", token=admin_token, method="POST")
            consents = request(base, "/admin/realms/v2x-app/users/" + user["id"] + "/consents", token=admin_token)
            for consent in consents:
                if consent.get("additionalGrants"):
                    request(base, "/admin/realms/v2x-app/users/" + user["id"] + "/consents/"
                            + urllib.parse.quote(consent["clientId"], safe=""), token=admin_token, method="DELETE")
        rotated = request(base, "/realms/v2x-app/protocol/openid-connect/certs")
        assert not thumbprints(before) & thumbprints(rotated), "Retired RSA keys remain published"
        rotated_keys = request(base, "/admin/realms/v2x-app/keys", token=admin_token)
        original_ids = {key["kid"] for key in original_keys["keys"]}
        assert not original_ids & {key["kid"] for key in rotated_keys["keys"]}, "A retired key is still registered"
        assert {"RS256", "RSA-OAEP", "HS512", "AES"} <= {key["algorithm"] for key in rotated_keys["keys"]}, \
            "A replacement provider did not generate its key"
        assert not verifies(old_token, rotated), "Old token still verifies after rotation"
        new_token = request(base, "/realms/v2x-app/protocol/openid-connect/token", form={
            "grant_type": "password", "client_id": "v2x-app-api", "client_secret": client_secret,
            "username": "admin", "password": "12345"})["access_token"]
        assert verifies(new_token, rotated), "New token fails after rotation"
        print("Four-provider rotation and old-token rejection verified", flush=True)
    finally:
        for container in reversed(containers):
            subprocess.run(["docker", "rm", "--force", "--volumes", container], capture_output=True)
        if network_created:
            subprocess.run(["docker", "network", "rm", prefix], capture_output=True)
        if built_image:
            subprocess.run(["docker", "image", "rm", image], capture_output=True)


if __name__ == "__main__":
    main()
