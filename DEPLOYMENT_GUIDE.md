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
  - ETX vendor credentials (Vendor ID, Username, Password)
  - Thingspace API credentials (Key, Secret)
  - Keycloak admin credentials

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
   KEYCLOAK_ENDPOINT=http://<host-ip>:8084
   KEYCLOAK_REALM=v2x-app
   KEYCLOAK_CLIENT_NAME=v2x-app-api
   KEYCLOAK_CLIENT_SECRET=<generate-32-character-secret>
   ```
   > **Note:** Generate a secure 32-character string for `KEYCLOAK_CLIENT_SECRET` using a password generator.

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
   > **Note:** Default users are for development only. Configure production users in Keycloak.

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
   - Configure production users in Keycloak (remove default users)

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

