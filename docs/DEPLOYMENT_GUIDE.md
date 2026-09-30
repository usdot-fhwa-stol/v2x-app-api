# V2X App API - Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying the V2X App API using Docker Compose. The application consists of three main services:

- **v2x-app-api** (Port 8080): Spring Boot REST API
- **keycloak** (Port 8084): Authentication service
- **postgres** (Port 5432): Database

## Prerequisites

- Docker and Docker Compose installed
- Access to the deployment server
- Required credentials:
  - [Verizon ETX](https://thingspace.verizon.com/documentation/api-documentation.html#/http/specialized-apis/edge-transportation-exchange/overview) vendor credentials (Vendor ID, Username, Password)
  - [Verizon Thingspace API](https://thingspace.verizon.com/documentation/api-documentation.html#/http/session-management/quick-api-reference) credentials (Key, Secret)
  - [Keycloak](https://www.keycloak.org/) admin credentials

## Deployment Steps

### Step 1: Prepare Environment File

1. Copy the sample environment file:
   ```bash
   cp sample.env .env
   ```

2. Edit `.env` and configure the following **required** variables:

   **Database Configuration:**
   ```
   POSTGRES_DB=v2x_app_db
   POSTGRES_USER=admin_user
   POSTGRES_PASSWORD=<secure-password>
   ```

   **Keycloak Configuration:**
   ```
   KEYCLOAK_ADMIN=admin
   KEYCLOAK_ADMIN_PASSWORD=<secure-password>
   KEYCLOAK_ENDPOINT=http://keycloak:8080
   KEYCLOAK_REALM=v2x-app
   KEYCLOAK_CLIENT_NAME=v2x-app-api
   KEYCLOAK_CLIENT_SECRET=<generate-32-character-secret>
   ```
   > **Note:** The sample administrator password `change_me_123` and client secret
   > `change_this_for_production_use` are retained for local development. Replace both
   > with independent, securely generated values before production use.

   `KEYCLOAK_ENDPOINT` is the single URL for token requests, JWKS fetching, and JWT
   issuer validation. The Compose default uses the `keycloak` service DNS name and
   container port `8080`. Compose configures `KC_HOSTNAME` to the same URL so tokens
   requested through either the API or Keycloak's published port share one issuer.
   Obtain fresh tokens after changing this URL.

   Docker-only DNS is unavailable to host/IDE clients and browsers. For host/IDE
   development, set `KEYCLOAK_ENDPOINT=http://localhost:8084` before starting Keycloak.
   For browser-based administration with a containerized API, or HTTPS deployments,
   use a full URL reachable from both the API container and external clients, such
   as `https://auth.example.com`. Recreate services after changing environment values.

   **ETX Configuration:**
   ```
   ETX_ENABLED=true
   ETX_ENDPOINT=https://imp.thingspace.verizon.com
   ETX_VENDOR_ID=<your-vendor-id>
   ETX_VENDOR_USERNAME=<your-username>
   ETX_VENDOR_PASSWORD=<your-password>
   ETX_DEPOSITOR_VENDOR_ID=<depositor-vendor-id>  # Optional, defaults to ETX_VENDOR_ID
   ```

   **Thingspace Configuration:**
   ```
   THINGSPACE_ENABLED=true
   THINGSPACE_KEY=<your-thingspace-key>
   THINGSPACE_SECRET=<your-thingspace-secret>
   THINGSPACE_ENDPOINT=https://thingspace.verizon.com
   ```

   **Optional Configuration:**
   - `DOCKER_HOST_IP`: Set to the server's IP address if accessing from external hosts
   - `RESTART_POLICY`: Set to `always` for production deployments
   - `DEPOSIT_MODE`: `ETX_CONFIGURATION_API` (default) or `GEOFENCE_MQTT`
   - Registration limits (see `sample.env` for defaults)

### Step 2: Deploy Services

1. Build and start all services:
   ```bash
   docker compose up --build -d
   ```

2. Verify services are running:
   ```bash
   docker compose ps
   ```

   You should see all three services (postgres, keycloak, v2x-app-api) in "Up" status.

### Step 3: Verify Deployment

1. **Check API Health:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```
   Expected response: `{"status":"UP"}`

2. **Check Keycloak:**
   ```bash
   curl http://localhost:8084/health
   ```
   Expected response: Health status JSON

3. **View API Logs:**
   ```bash
   docker compose logs -f v2x-app-api
   ```
   Look for "Started V2XAppApiApplication" to confirm successful startup.

4. **Access Swagger UI:**
   Open in browser: `http://<host-ip>:8080/swagger-ui.html`

### Step 4: Verify Authentication

1. Obtain an access token:
   ```bash
   curl -X POST http://localhost:8080/auth/token \
     -H "Content-Type: application/json" \
     -d '{
       "username": "admin",
       "password": "12345"
     }'
   ```
   > **Note:** This example uses the seeded development account. In production, use an
   > individually provisioned account after completing the checklist below.

2. Test authenticated endpoint:
   ```bash
   curl -X GET http://localhost:8080/prd/v2/registration \
     -H "Authorization: Bearer <your-token>"
   ```

## Service Management

### View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f v2x-app-api
docker compose logs -f keycloak
docker compose logs -f postgres
```

### Stop Services

```bash
docker compose down
```

### Restart Services

```bash
docker compose restart
```

### Update and Redeploy

1. Pull latest code changes
2. Rebuild and restart:
   ```bash
   docker compose up --build -d
   ```

## Docker Compose Profiles

Control which services start using profiles:

- `postgres`: PostgreSQL only
- `keycloak`: Keycloak + PostgreSQL
- `v2x-app-api`: API + PostgreSQL
- `all`: All services (default)

Example - Start only API and database:
```bash
COMPOSE_PROFILES=v2x-app-api docker compose up -d
```

## Port Configuration

| Service | Port | Description |
|---------|------|-------------|
| v2x-app-api | 8080 | REST API |
| keycloak | 8084 | Authentication UI |
| keycloak | 8090 | Management API |
| postgres | 5432 | Database |

Ensure these ports are available and not blocked by firewall rules.

## Troubleshooting

### Services Won't Start

1. Check logs:
   ```bash
   docker compose logs
   ```

2. Verify environment variables:
   ```bash
   docker compose config
   ```

3. Check port availability:
   ```bash
   netstat -tuln | grep -E '8080|8084|5432'
   ```

### Keycloak Connection Issues

1. Verify Keycloak is accessible:
   ```bash
   curl http://localhost:8084/health
   ```

2. Check Keycloak logs:
   ```bash
   docker compose logs keycloak
   ```

3. Verify `KEYCLOAK_ENDPOINT` in `.env` matches your server's IP/hostname

### Database Connection Issues

1. Verify PostgreSQL is running:
   ```bash
   docker compose ps postgres
   ```

2. Check database logs:
   ```bash
   docker compose logs postgres
   ```

3. Verify database credentials in `.env`

### API Health Check Fails

1. Check API logs for errors:
   ```bash
   docker compose logs v2x-app-api
   ```

2. Verify all required environment variables are set

3. Check if Keycloak is accessible from the API container:
   ```bash
   docker compose exec v2x-app-api curl http://keycloak:8080/health
   ```

## Production Considerations

1. **Security:**
   - Change all default passwords
   - Use strong, unique passwords for all services
   - Generate secure `KEYCLOAK_CLIENT_SECRET` (32 characters)
   - Generate secure `KEYCLOAK_ADMIN_PASSWORD` (32 characters)
   - Complete the [production credential checklist](#production-credential-checklist)
   - Follow Keycloak's guidance for [deploying in production](https://www.keycloak.org/server/configuration-production)

2. **Restart Policy:**
   - Set `RESTART_POLICY=always` in `.env` for automatic restarts

3. **Logging:**
   - Configure log rotation (already set to 10MB max, 5 files)
   - Set appropriate `API_LOGGING_LEVEL` (INFO or WARN for production)

4. **Backup:**
   - Regularly backup PostgreSQL data volume
   - Backup Keycloak configuration and realm data

5. **Monitoring:**
   - Set up health check monitoring for `/actuator/health`
   - Monitor Prometheus metrics at `/actuator/prometheus`

6. **Network:**
   - Configure firewall rules for [required ports](#port-configuration)
   - Use reverse proxy (nginx/traefik) for HTTPS termination

## Keycloak Key Security and Recovery

The realm template preserves generated-provider settings but contains no RSA private
keys/certificates, AES/HMAC secrets, or exported key identifiers. A fresh import
generates unique material and stores realm state in PostgreSQL. The Keycloak container
does not mount a persistent volume over its import directory, so a rebuilt image uses
the updated template. PostgreSQL data remains persistent.

**Existing realms need explicit recovery.** Keycloak skips startup imports when a realm
already exists. Updating the source, rebuilding, restarting, or changing the client
secret does not replace its signing keys. See
[Keycloak import behavior](https://www.keycloak.org/server/importExport).

### Production Credential Checklist

Before making the API or Keycloak publicly reachable:

1. Replace `KEYCLOAK_ADMIN_PASSWORD=change_me_123` with a unique administrator password.
   For an existing installation, change the bootstrap administrator's password in the
   **master** realm as well; changing the environment variable does not reset an
   existing administrator account.
2. Replace `KEYCLOAK_CLIENT_SECRET=change_this_for_production_use` with a unique secret.
   For an existing realm, regenerate the `v2x-app-api` client's secret in Keycloak and
   update the API environment to match. A new environment value alone does not modify
   an already imported client. Restart the API after updating it.
3. In **v2x-app**, create individually credentialed users and assign only the required
   `ROLE_USER`, `ROLE_DEPOSITOR`, or `ROLE_ADMIN` roles. These are application users,
   separate from the Keycloak administrator in the master realm.
4. Delete the seeded **user**, **depositor**, and **admin** accounts from **v2x-app**.
   Revoke their online and offline sessions before deletion. Verify all three accounts
   can no longer obtain tokens with the documented password `12345`.
5. Confirm replacement accounts can authenticate and access only their intended APIs.
   If the realm used the earlier key-bearing template, also complete recovery below.

### Recover an Existing Realm Without Deleting PostgreSQL Data

Use this procedure for every environment bootstrapped from the earlier template.
Development environments may keep their seeded users and local defaults.

1. Back up PostgreSQL securely and record the existing key-provider IDs and public
   key IDs. Treat backups containing the old key material as sensitive and unsuitable
   for restoring a recovered realm without repeating this procedure.
2. Stop **every API instance** and block external access during recovery. For the local
   Compose stack:

   ```bash
   docker compose stop v2x-app-api
   docker compose build keycloak
   docker compose up -d --no-deps --force-recreate keycloak
   ```

   Preserve the PostgreSQL service and volume. Do **not** use `docker compose down -v`,
   delete the database, or overwrite the entire realm.
3. In the Keycloak admin console, select **v2x-app → Realm settings → Keys → Providers**.
   Create fresh providers without supplying any existing key material, using priority
   `200` to supersede the existing priority `100`:

   | Provider | Settings |
   |----------|----------|
   | `rsa-generated` | Signing (`SIG`), `RS256` |
   | `rsa-enc-generated` | Encryption (`ENC`), `RSA-OAEP` |
   | `hmac-generated` | `HS512` |
   | `aes-generated` | AES; retain the existing key-size setting/default |

   Give the replacements distinct names and confirm all four generated keys appear.
4. Delete the **old** providers identified in step 1, including any historical providers
   carrying the exposed material. Making the old signing key passive or lowering its
   priority is insufficient: passive keys can still verify forged tokens. See
   [Keycloak key management](https://www.keycloak.org/docs/latest/server_admin/index.html#_keys).
5. Sign out all realm sessions. Separately revoke offline grants/sessions for affected
   users through the user **Consents/Offline access** controls (or the corresponding
   Admin REST API); ordinary logout alone does not revoke offline access.
6. Fetch the realm's public JWKS at
   `<KEYCLOAK_ENDPOINT>/realms/v2x-app/protocol/openid-connect/certs`. Verify that old
   key IDs are absent and compare RSA public-key thumbprints with
   [`scripts/tests/compromised-keycloak-public-keys.json`](../scripts/tests/compromised-keycloak-public-keys.json).
   That fixture contains SHA-256 JWK thumbprints (RFC 7638), not private keys. From the
   repository root, this check prints only public fingerprints:

   ```bash
   KEYCLOAK_ENDPOINT=https://auth.example.com python3 - <<'PY'
   import json, os
   from pathlib import Path
   from scripts.test_keycloak_import import request, thumbprints
   live = thumbprints(request(os.environ['KEYCLOAK_ENDPOINT'].rstrip('/'),
                            '/realms/v2x-app/protocol/openid-connect/certs'))
   exposed = {k['sha256_jwk_thumbprint'] for k in json.loads(
       Path('scripts/tests/compromised-keycloak-public-keys.json').read_text())}
   print('Current public RSA thumbprints:', ', '.join(sorted(live)))
   if live & exposed:
       raise SystemExit('FAIL: exposed RSA key is still published')
   print('PASS: exposed RSA keys are absent')
   PY
   ```

   Substitute your actual endpoint and realm if customized. Also verify the AES and
   HMAC provider IDs/key IDs differ from those recorded before recovery.
7. Start or recreate every API instance so its in-memory JWKS cache is cleared:

   ```bash
   docker compose up -d --no-deps --force-recreate v2x-app-api
   ```

   Resource servers cache verification keys, so removing a key from Keycloak does not
   guarantee immediate rejection by an already running API. See
   [Spring JWKS caching](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
8. With external access still blocked, submit an access token obtained before rotation
   to a protected, read-only endpoint and verify HTTP **401**. Obtain a fresh token and
   confirm normal access. Do not print tokens or use real integration credentials in
   regression tests. Reopen access only after every API instance passes.
9. After verification, retire obsolete Keycloak images and the old, unused Keycloak
   import volume by their exact recorded names. Do not remove `postgres_data` or use
   broad Docker pruning. Never roll back to an image, import file, or database backup
   that restores the exposed keys. Historical Git copies remain compromised.

The CI import test uses disposable services to verify independent installations,
restart persistence, retained development accounts, and this key-rotation sequence:

```bash
python3 scripts/test_keycloak_import.py
```

`[PASS]` messages describe successful checks in the temporary installations. The final
`[RESULT] PASS` means the tested image generates independent keys, preserves them across
restart, and supports rotation that removes old keys from verification. It is not a
security assessment of an existing deployment. `[FAIL]` identifies a failed test check;
`[ERROR]` / `[RESULT] INCOMPLETE` means the test could not finish, for example because
Docker or Keycloak failed to start. Both failure cases exit with a nonzero status.

## Access Points

After successful deployment:

- **API Base URL:** `http://<host-ip>:8080`
- **Swagger UI:** `http://<host-ip>:8080/swagger-ui.html`
- **API Docs:** `http://<host-ip>:8080/api-docs`
- **Keycloak Admin:** `http://<host-ip>:8084`
- **Health Check:** `http://<host-ip>:8080/actuator/health`

## Support

For additional information, refer to:
- Full README: `README.md`
- API Documentation: Swagger UI at `/swagger-ui.html`
- Configuration Reference: `sample.env`

