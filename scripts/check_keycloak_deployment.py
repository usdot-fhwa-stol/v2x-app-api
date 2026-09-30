#!/usr/bin/env python3
"""Read-only check for the exposed F-01 RSA keys in an existing Keycloak realm.

Requires only Python 3 and access to the realm's public certs endpoint.
Exit codes: 0 = known RSA keys absent; 1 = exposed key found; 2 = incomplete.
This does not assess symmetric keys, disabled providers, or API key caches.
"""

import argparse
import base64
import hashlib
import http.client
import json
import math
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


KNOWN_KEYS = Path(__file__).with_name("compromised-keycloak-public-keys.json")
MAX_JWKS_BYTES = 1024 * 1024


class AuditError(Exception):
    """A check could not finish; messages must not include response contents."""


def certs_url(base, realm):
    try:
        parts = urllib.parse.urlsplit(base)
        port = parts.port
    except ValueError:
        raise AuditError("Invalid Keycloak URL.") from None
    if (parts.scheme not in ("http", "https") or not parts.hostname
            or parts.username is not None or parts.password is not None
            or parts.query or parts.fragment or (port is not None and port == 0)
            or any(char.isspace() or ord(char) < 32 for char in base)):
        raise AuditError("Use an HTTP(S) Keycloak base URL without credentials, query, or fragment.")
    if not realm.strip():
        raise AuditError("Realm name must not be empty.")
    return base.rstrip("/") + "/realms/" + urllib.parse.quote(realm, safe="") + "/protocol/openid-connect/certs"


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # A different endpoint could publish a different realm's keys.
        raise AuditError("JWKS endpoint redirected; supply the final Keycloak base URL.")


def fetch_jwks(url, timeout):
    request = urllib.request.Request(url, headers={"Accept": "application/json"}, method="GET")
    try:
        with urllib.request.build_opener(NoRedirects()).open(request, timeout=timeout) as response:
            raw = response.read(MAX_JWKS_BYTES + 1)
    except urllib.error.HTTPError as error:
        raise AuditError(f"JWKS request returned HTTP {error.code}.") from None
    except (urllib.error.URLError, OSError, ValueError, http.client.HTTPException):
        raise AuditError("Could not retrieve JWKS; check the URL, network access, and TLS certificate.") from None
    if len(raw) > MAX_JWKS_BYTES:
        raise AuditError("JWKS response exceeds the size limit.")
    try:
        return json.loads(raw)
    except (ValueError, UnicodeError, RecursionError):
        raise AuditError("JWKS response is not valid JSON.") from None


def rsa_thumbprint(key):
    """RFC 7638 thumbprint, independent of key ID, usage, and algorithm labels."""
    for field in ("e", "n"):
        value = key.get(field)
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]+", value):
            raise AuditError(f"RSA key has a missing or invalid {field} field.")
        try:
            decoded = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
        except ValueError:
            raise AuditError(f"RSA key has an invalid {field} field.") from None
        if (not decoded or decoded[0] == 0
                or base64.urlsafe_b64encode(decoded).decode().rstrip("=") != value):
            raise AuditError(f"RSA key has a noncanonical {field} field.")
    public = {field: key[field] for field in ("e", "kty", "n")}
    digest = hashlib.sha256(json.dumps(public, sort_keys=True, separators=(",", ":")).encode()).digest()
    return base64.urlsafe_b64encode(digest).decode().rstrip("=")


def load_known_keys():
    try:
        entries = json.loads(KNOWN_KEYS.read_text())
        known = {entry["sha256_jwk_thumbprint"]: entry["provider"] for entry in entries}
        if (len(entries) != 2 or len(known) != 2
                or set(known.values()) != {"rsa-generated", "rsa-enc-generated"}
                or any(not re.fullmatch(r"[A-Za-z0-9_-]{43}", value) for value in known)):
            raise ValueError
        return known
    except (OSError, ValueError, KeyError, TypeError):
        raise AuditError("Could not load the two known exposed RSA fingerprints.") from None


def inspect_jwks(jwks, known):
    if not isinstance(jwks, dict) or not isinstance(jwks.get("keys"), list) or not jwks["keys"]:
        raise AuditError("JWKS must contain a nonempty keys array.")
    fingerprints = set()
    for key in jwks["keys"]:
        if not isinstance(key, dict) or not isinstance(key.get("kty"), str):
            raise AuditError("JWKS contains an invalid key entry.")
        if key["kty"] == "RSA":
            fingerprints.add(rsa_thumbprint(key))
    if not fingerprints:
        raise AuditError("JWKS contains no RSA keys; the known RSA keys could not be assessed.")
    return sorted(known[fingerprint] for fingerprint in fingerprints & known.keys()), len(fingerprints)


def positive_timeout(value):
    try:
        timeout = float(value)
    except ValueError:
        raise argparse.ArgumentTypeError("timeout must be a positive number") from None
    if not math.isfinite(timeout) or timeout <= 0:
        raise argparse.ArgumentTypeError("timeout must be a positive number")
    return timeout


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keycloak-url", required=True, help="Reachable base URL, e.g. http://localhost:8084")
    parser.add_argument("--realm", default="v2x-app", help="Realm to inspect (default: v2x-app)")
    parser.add_argument("--timeout", type=positive_timeout, default=10, help="Request timeout in seconds (default: 10)")
    args = parser.parse_args(argv)
    print("[SCOPE] Read-only audit of an existing deployment; no users, keys, sessions, or data are changed.")
    print("[LIMIT] Public JWKS cannot assess AES/HMAC secrets, disabled providers, or API cached keys.")
    try:
        url = certs_url(args.keycloak_url, args.realm)
        known = load_known_keys()
        print(f"[CHECK] Fetching {url}", flush=True)
        matches, count = inspect_jwks(fetch_jwks(url, args.timeout), known)
    except AuditError as error:
        print(f"[RESULT] INCOMPLETE: {error}", file=sys.stderr)
        return 2
    for provider in matches:
        print(f"[FOUND] Known exposed RSA key from the old {provider} provider is still published.")
    if matches:
        print("[RESULT] VULNERABLE: exposed RSA key material from F-01 is still present in this realm's public JWKS.")
        print("[ACTION] Follow the deployment guide's recovery procedure to replace all four old providers, "
              "revoke sessions, and restart every API instance.")
        return 1
    print(f"[RESULT] KNOWN RSA KEYS ABSENT: checked {count} distinct published RSA keys; "
          "neither matches the two exposed keys from F-01.")
    print("[LIMIT] This does not establish full recovery or production readiness. "
          "For realms imported from the old template, also complete the documented recovery checks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
