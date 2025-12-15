# V2X App API

A Spring Boot REST API that acts as an intermediary between mobile applications and the Verizon MEC (Multi-Access Edge Computing) API. The application provides authentication, ETX client registration, V2X message deposit, and J2735 message decoding services.

## Overview

The V2X App API facilitates:
- **Authentication**: Keycloak-based OAuth2/OIDC authentication with role-based access control
- **ETX Registration**: Client registration and connection management for the ETX MQTT Broker
- **V2X Message Deposit**: Deployment of V2X messages (TIM, MAP, etc.) with geospatial geofencing
- **J2735 Decoding**: Conversion of ASN.1 encoded V2X messages between UPER (binary) and XER/JSON formats
- **Path Management**: GeoJSON path data management
- **TIM Configuration**: Traveler Information Message configuration and metadata

## Architecture

The application consists of four main services:

1. **v2x-app-api** (Port 8080): Spring Boot REST API
   - Java 22 with Foreign Function & Memory API for native J2735 codec integration
   - PostgreSQL for persistence (registration logs, geofence deployments, limits, MQTT clients, ACLs)
   - OAuth2 Resource Server with Keycloak integration

2. **keycloak** (Port 8084): Authentication service
   - OAuth2/OIDC provider
   - User and role management

3. **postgres** (Port 5432): Database
   - Stores registration logs, geofence deployments, vendor/user registration limits, MQTT clients, ACLs

4. **mosquitto** (Ports 1883, 8883): MQTT Broker
   - Self-hosted MQTT broker with TLS support
   - Self-signed certificate support
   - ACL-based topic access control
   - Geohash-based topic structure: `/v2x/geohash/{level1}.../{level7}/{messageType}`

## Prerequisites

- **Java 22+** (required for FFM API)
- **Docker & Docker Compose** (for containerized deployment)
- **PostgreSQL 17** (if running database separately)
- **Native Library**: `libasnapplication.so` (Linux) or `asnapplication.dll` (Windows)
  - Located in `j2735-ffm-java/lib/`
  - For local development, copy to `/usr/lib/` (Linux) or system PATH (Windows)

## Configuration

### Environment Setup

1. Copy the sample environment file:
   ```bash
   cp sample.env .env
   ```

2. Configure required variables in `.env`:

   **Docker Configuration:**
   - `DOCKER_HOST_IP`: Docker host IP address (used for service URLs)
   - `COMPOSE_PROFILES`: Docker Compose profiles to use (default: `all`). Available profiles: `postgres`, `keycloak`, `v2x-app-api`, `all`
   - `RESTART_POLICY`: Docker container restart policy (default: `"no"`). See [Docker documentation](https://docs.docker.com/engine/containers/start-containers-automatically/) for options.

   **Keycloak Configuration:**
   - `KEYCLOAK_ENDPOINT`: Keycloak server URL (default: `http://${DOCKER_HOST_IP}:8084`)
   - `KEYCLOAK_REALM`: Realm name (default: `v2x-app`)
   - `KEYCLOAK_CLIENT_NAME`: Client ID (default: `v2x-app-api`)
   - `KEYCLOAK_CLIENT_SECRET`: Client secret (generate a secure 32-character string)
   - `KEYCLOAK_ADMIN`: Keycloak admin username (default: `admin`)
   - `KEYCLOAK_ADMIN_PASSWORD`: Keycloak admin password

   **ETX Configuration:**
   - `ETX_ENABLED`: Enable ETX integration (default: `true`)
   - `ETX_ENDPOINT`: ETX API endpoint (default: `https://imp.thingspace.verizon.com`)
   - `ETX_VENDOR_ID`: Vendor ID for registrations
   - `ETX_VENDOR_USERNAME`: ETX vendor username
   - `ETX_VENDOR_PASSWORD`: ETX vendor password
   - `ETX_DEPOSITOR_VENDOR_ID`: Separate vendor ID for depositor role (defaults to `ETX_VENDOR_ID`)
   - `GEOFENCE_CLEANUP_ENABLED`: Enable periodic cleanup of inactive TIM geofences (default: `false`)
   - `GEOFENCE_CLEANUP_INTERVAL`: Cleanup interval duration (default: `5m`). Supports duration formats like `5m`, `30s`, `1h`, or ISO-8601 format like `PT5M`.

   **Thingspace Configuration:**
   - `THINGSPACE_ENABLED`: Enable Thingspace integration (default: `true`)
   - `THINGSPACE_KEY`: Thingspace API key
   - `THINGSPACE_SECRET`: Thingspace API secret
   - `THINGSPACE_ENDPOINT`: Thingspace endpoint (default: `https://thingspace.verizon.com`)
   - `THINGSPACE_SESSION_TOKEN_LIFESPAN`: Session token lifespan duration (default: `10m`). Supports duration formats like `10m`, `30s`, `1h`, or ISO-8601 format like `PT10M`.
   - `TOKEN_PERIODIC_REGENERATION`: Enable periodic token regeneration to prevent expiration (default: `true`)

   **Database Configuration:**
   - `POSTGRES_DB`: Database name (default: `v2x_app_db`)
   - `POSTGRES_USER`: Database user (default: `admin_user`)
   - `POSTGRES_PASSWORD`: Database password
   - `SPRING_DATASOURCE_USERNAME`: Spring DataSource username (defaults to `POSTGRES_USER`)
   - `SPRING_DATASOURCE_PASSWORD`: Spring DataSource password (defaults to `POSTGRES_PASSWORD`)

   **Registration Limits:**
   - `ETX_VENDOR_REGISTRATION_LIMIT`: Max registrations per vendor (default: `50`)
   - `ETX_VENDOR_USER_REGISTRATION_LIMIT`: Max registrations per user (default: `5`)
   - `ETX_DEPOSITOR_VENDOR_REGISTRATION_LIMIT`: Max registrations for depositor vendor (default: `10`)
   - `ETX_DEPOSITOR_VENDOR_USER_REGISTRATION_LIMIT`: Max registrations per depositor user (default: `3`)

   **Geofence Configuration:**
   - `GEOFENCE_OFFSET_METERS`: Geofence offset in meters (default: `100.0`)
   - `DEFAULT_LANE_WIDTH_CM`: Default lane width in centimeters (default: `1500.0`)
   - `GEOFENCE_GEOHASH_PRECISION`: Geohash precision for geofence generation (default: `7`). This determines the spatial resolution of geohash-based geofences. See [Geohash precision documentation](https://en.wikipedia.org/wiki/Geohash#Digits_and_precision_in_km) for details on how precision affects geographic area coverage.
   - `GEOFENCE_LIMITS_MAX_GEOHASHES`: Maximum number of geohashes allowed per geofence deployment (default: `500`)
   - `GEOFENCE_CLEANUP_ENABLED`: Enable periodic cleanup of inactive TIM geofences (default: `false`)
   - `GEOFENCE_CLEANUP_INTERVAL`: Cleanup interval duration (default: `5m`). Supports duration formats like `5m`, `30s`, `1h`, or ISO-8601 format like `PT5M`.
   - `GEOFENCE_EXPIRATION_CLEANUP_ENABLED`: Enable cleanup of expired geofences (default: `true`)
   - `GEOFENCE_EXPIRATION_CLEANUP_INTERVAL`: Expiration cleanup interval duration (default: `5m`). Supports duration formats like `5m`, `30s`, `1h`, or ISO-8601 format like `PT5M`.
   - `GEOFENCE_EXPIRATION_GRACE_PERIOD`: Grace period for geofence expiration (default: `2h`). Supports duration formats like `2h`, `30m`, `1d`, or ISO-8601 format like `PT2H`, `PT30M`, `P1D`.

   **Deployment Mode:**
   - `DEPOSIT_MODE`: Deployment mode for TIM messages (default: `ETX_CONFIGURATION_API`). Options: `ETX_CONFIGURATION_API` (use ETX Configuration API for deployments) or `GEOFENCE_MQTT` (store in database for MQTT broker distribution)

   **Mosquitto MQTT Broker Configuration:**
   - `MOSQUITTO_ENABLED`: Enable Mosquitto MQTT broker (default: `false`)
   - `MOSQUITTO_HOST`: Mosquitto broker hostname (default: `mosquitto`)
   - `MOSQUITTO_PORT`: MQTT port (default: `1883`)
   - `MOSQUITTO_TLS_PORT`: MQTT TLS port (default: `8883`)
   - `MOSQUITTO_USE_TLS`: Enable TLS for MQTT connections (default: `true`)
   - `MOSQUITTO_CERTS_DIRECTORY`: Directory path for certificates (default: `/mosquitto/certs`)
   - `MOSQUITTO_ACL_FILE`: Path to ACL configuration file (default: `/mosquitto/acl/acl.conf`)
   - `MOSQUITTO_PASSWORD_FILE`: Path to password file (default: `/mosquitto/config/passwd`)
   - `MOSQUITTO_SERVER_CN`: Server certificate common name (default: `mosquitto`)

   **TIM Configuration:**
   - `TIM_CONFIG_FILE_PATH`: Path to TIM configuration file (default: `/tim_config_files/tim-config.json`). Use full system path if debugging in IDE, otherwise use relative path to the project root.
   - `TIM_ICONS_DIRECTORY`: Directory path for TIM icons (default: `/tim_config_files/tim-icons`)

   **Secret Configuration:**
   - `ISS_SCMS_TOKEN`: ISS SCMS token
   - `S3_ACCESS_KEY`: AWS S3 access key
   - `S3_SECRET_KEY`: AWS S3 secret key
   - `S3_BUCKET_NAME`: AWS S3 bucket name
   - `S3_REGION`: AWS S3 region
   - `S3_DESTINATION`: AWS S3 destination path
   - `MAPBOX_ACCESS_TOKEN`: Mapbox access token
   - `NOAA_GEOMAG_API_TOKEN`: NOAA Geomagnetic API token

   **Logging Configuration:**
   - `KC_LOGGING_LEVEL`: Keycloak logging level (default: `"WARN"`). Options: `"ALL"`, `"FATAL"`, `"OFF"`, `"TRACE"`, `"WARN"`
   - `API_LOGGING_LEVEL`: API logging level (default: `INFO`). Options: `"TRACE"`, `"DEBUG"`, `"INFO"`, `"SUCCESS"`, `"WARNING"`, `"ERROR"`, `"CRITICAL"`

### Docker Compose Profiles

Control which services start using profiles:
- `postgres`: PostgreSQL only
- `keycloak`: Keycloak + PostgreSQL
- `v2x-app-api`: API + PostgreSQL
- `mosquitto`: Mosquitto + PostgreSQL
- `all`: All services (default)

Example:
```bash
docker compose up COMPOSE_PROFILES=v2x-app-api
```

### Mosquitto Certificate Setup

Before starting Mosquitto, generate the CA and server certificates:

```bash
cd resources/mosquitto
./generate-certs.sh
```

This will create:
- `certs/ca.crt` - CA certificate
- `certs/ca.key` - CA private key
- `certs/server.crt` - Server certificate
- `certs/server.key` - Server private key

Client certificates are generated automatically when registering clients via the API with `generateCertificate: true`.

**Authentication Method:**
- **Certificate-based only**: Clients connect using TLS (port 8883) with client certificates. The API generates certificates automatically when registering clients with `generateCertificate: true`. Mosquitto uses the certificate CN (Common Name) as the username for ACL matching. All certificate information (CN, serial number, expiration) is tracked and stored in the PostgreSQL database.

**Certificate Tracking in Database:**
When a client registers and gets a certificate, the following information is stored in the `mqtt_clients` table:
- `certificate_cn`: Certificate Common Name (used as username in ACL)
- `certificate_serial`: Certificate serial number
- `certificate_expires_at`: Certificate expiration timestamp
- `last_connected_at`: Last connection timestamp (updated on each connection)

**Note**: The configuration requires client certificates for TLS connections. Non-TLS connections (port 1883) are disabled by default for security.

**Topic Structure:**
MQTT topics follow the geohash-based structure:
```
/v2x/geohash/{level1}/{level2}/{level3}/{level4}/{level5}/{level6}/{level7}/{messageType}
```

Where:
- `level1` through `level7` are individual characters from a geohash (up to 7 precision levels)
- `messageType` is the V2X message type (BSM, SPAT, MAP, TIM, etc.)

Example topics:
- `/v2x/geohash/9/q/8/y/y/m/h/BSM` - BSM messages for geohash "9q8yymh"
- `/v2x/geohash/9/q/8/+/+/+/+/BSM` - BSM messages for any geohash starting with "9q8" (wildcard)
- `/v2x/geohash/9/q/8/#` - All message types for geohashes starting with "9q8" (multi-level wildcard)

## Building and Running

### Docker Compose (Recommended)

Build and start all services:
```bash
docker compose up --build -d
```

View logs:
```bash
docker compose logs -f v2x-app-api
```

Stop services:
```bash
docker compose down
```

### Local Development

1. **Setup Native Library** (Linux/WSL):
   ```bash
   sudo cp ./j2735-ffm-java/lib/libasnapplication.so /usr/lib/
   ```

2. **Start PostgreSQL and Keycloak**:
   ```bash
   COMPOSE_PROFILES=keycloak docker compose up -d
   ```

3. **Run the Application**:
   ```bash
   cd v2x-app-api
   ./gradlew bootRun
   ```

   Or use VS Code launch configuration: "Java V2X App API"

4. **Access the API**:
   - API: [http://localhost:8080](http://localhost:8080)
   - Swagger UI: [http://localhost:8080/swagger-ui.html](`http://localhost:8080/swagger-ui.html`)
   - API Docs: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)
   - Keycloak: [http://localhost:8084](http://localhost:8084)

## API Endpoints

### Authentication
- `POST /auth/token` - Obtain JWT access token (no auth required)

### Registration
- `POST /prd/v2/registration` - Register ETX client
- `GET /prd/v2/registration` - List registrations
- `GET /prd/v2/registration/{id}` - Get registration details
- `PUT /prd/v2/registration/{id}` - Update registration
- `DELETE /prd/v2/registration/{id}` - Delete registration
- `POST /prd/v2/registration/{id}/connection` - Create connection
- `GET /prd/v2/registration/{id}/connection` - Get connection status
- `DELETE /prd/v2/registration/{id}/connection` - Delete connection

### Deposit
- `POST /prd/v2/deposit/geofence` - Deposit V2X message with geofence
- `DELETE /prd/v2/deposit/geofence?identifier={id}` - Delete geofence deployment

### Path Management
- `GET /prd/v2/paths` - Get all paths (GeoJSON format)
- `POST /prd/v2/paths` - Create path
- `GET /prd/v2/paths/{id}` - Get path by ID
- `PUT /prd/v2/paths/{id}` - Update path
- `DELETE /prd/v2/paths/{id}` - Delete path

### TIM Configuration
- `GET /prd/v2/tim/configuration` - Get TIM configuration (ITIS phrases, icons, metadata)
- `GET /prd/v2/tim/icons` - Download TIM icons ZIP

### Admin Endpoints
- `GET /prd/v2/admin/vendor-limits` - Get vendor registration limits
- `PUT /prd/v2/admin/vendor-limits` - Update vendor limits
- `GET /prd/v2/admin/user-limits` - Get user registration limits
- `PUT /prd/v2/admin/user-limits` - Update user limits
- `GET /prd/v2/admin/secrets` - Get secret configuration
- `PUT /prd/v2/admin/secrets` - Update secret configuration

### MQTT Client Management
- `POST /prd/v2/mqtt/clients` - Register MQTT client
- `GET /prd/v2/mqtt/clients` - List MQTT clients for current user
- `GET /prd/v2/mqtt/clients/{clientId}` - Get MQTT client details
- `DELETE /prd/v2/mqtt/clients/{clientId}` - Delete MQTT client
- `GET /prd/v2/mqtt/clients/{clientId}/certificate` - Get client certificate bundle

### MQTT ACL Management
- `POST /prd/v2/mqtt/acl` - Create or update ACL entry
- `GET /prd/v2/mqtt/acl/{clientId}` - Get ACL entries for client
- `DELETE /prd/v2/mqtt/acl/{aclId}` - Delete ACL entry
- `POST /prd/v2/mqtt/acl/regenerate` - Regenerate Mosquitto ACL file (admin only)

### Decode (No Authentication Required)
- `POST /api/v2/decode/hex` - Decode ASN.1 hex message to JSON

### Health & Monitoring
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Prometheus metrics

## Authentication & Authorization

### Roles

| Role | Description | Access |
|------|-------------|--------|
| `ROLE_USER` | Standard user | Registration, connection, path management, TIM configuration |
| `ROLE_DEPOSITOR` | Data depositor | All user permissions + V2X message deposit |
| `ROLE_ADMIN` | Administrator | Full access including admin endpoints |

### Default Users (Local Development Only)

| Username | Password | Role |
|----------|----------|------|
| `user` | `12345` | ROLE_USER |
| `depositor` | `12345` | ROLE_DEPOSITOR |
| `admin` | `12345` | ROLE_ADMIN |

### Obtaining a Token

```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "12345"
  }'
```

### Using the Token

```bash
curl -X GET http://localhost:8080/prd/v2/registration \
  -H "Authorization: Bearer <your-token>"
```

## J2735 Message Encoding

The API uses the J2735-2024 FFM library for encoding/decoding V2X messages:

- **XER** (XML Encoding Rules): Human-readable XML format
- **UPER** (Unaligned Packed Encoding Rules): Binary format
- **JER** (JSON Encoding Rules): JSON format (for decode endpoint)

The native library (`libasnapplication.so` / `asnapplication.dll`) is required and must be accessible at runtime.

## Key Features

### Registration Management
- Automatic vendor ID assignment based on user role
- Registration limit enforcement (vendor and user level)
- Connection lifecycle management
- Retry logic for pending registrations

### Geofence Deployments
- Geohash-based geofence generation
- Configurable geofence offset and lane width
- Automatic expiration cleanup
- Maximum geohash limits per deployment

### TIM Configuration
- ITIS phrase management
- Icon metadata and versioning
- Geohash precision configuration (see [Geohash precision documentation](https://en.wikipedia.org/wiki/Geohash#Digits_and_precision_in_km))
- Expiration grace periods

### Path Management
- GeoJSON-like path storage
- Timestamp and metadata tracking
- CRUD operations for path data

### MQTT Broker Integration
- Self-hosted Mosquitto broker with TLS support
- Self-signed certificate generation
- Client registration and certificate management via API
- ACL-based topic access control
- Geohash-based topic structure: `/v2x/geohash/{level1}/{level2}/.../{level7}/{messageType}`
  - Supports message types: BSM, SPAT, MAP, TIM, etc.
  - Wildcard support for topic subscriptions (`+` for single level, `#` for multi-level)
- Automatic ACL file regeneration from database

## Development

### Project Structure

```
v2x-app-api/
├── src/main/java/usdot/v2x/app/api/
│   ├── config/          # Configuration classes
│   ├── decode/          # J2735 decode endpoints
│   ├── deposit/         # V2X message deposit
│   ├── etx/             # ETX integration (registration, configuration)
│   ├── keycloak/       # Authentication endpoints
│   ├── path/           # Path management
│   ├── secret/         # Secret management
│   ├── tim/            # TIM configuration
│   └── services/       # Business logic services
├── src/main/resources/
│   └── application.yml # Application configuration
└── build.gradle        # Build configuration
```

### Building

```bash
cd v2x-app-api
./gradlew clean build
```

### Testing

```bash
./gradlew test
```

### OpenAPI Documentation

Generate OpenAPI spec:
```bash
./gradlew generateOpenApiDocs
```

Output: `docs/v2x-app-api-openapi.json`

## Dependencies

- **Spring Boot 3.4.5**: Web framework
- **Spring Security**: OAuth2 resource server
- **PostgreSQL**: Database
- **J2735-2024-FFM-Lib**: Native J2735 codec (via FFM API)
- **JPO ASN Runtime**: ASN.1 runtime library
- **GeoTools**: Geospatial operations
- **JTS**: Geometry operations

## Troubleshooting

### Native Library Not Found
- **Linux/WSL**: Ensure `libasnapplication.so` is in `/usr/lib/` or `LD_LIBRARY_PATH`
- **Windows**: Ensure `asnapplication.dll` is in system PATH
- Check library path in `application.yml`: `j2735.codec.library-path`

### Keycloak Connection Issues
- Verify `KEYCLOAK_ENDPOINT` is accessible
- Check Keycloak logs: `docker compose logs keycloak`
- Ensure realm and client are configured correctly

### Database Connection Issues
- Verify PostgreSQL is running: `docker compose ps postgres`
- Check connection string in `.env`
- Review database logs: `docker compose logs postgres`

## License

See LICENSE file for details.
