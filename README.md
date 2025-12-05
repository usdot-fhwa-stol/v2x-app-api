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

The application consists of three main services:

1. **v2x-app-api** (Port 8080): Spring Boot REST API
   - Java 22 with Foreign Function & Memory API for native J2735 codec integration
   - PostgreSQL for persistence (registration logs, geofence deployments, limits)
   - OAuth2 Resource Server with Keycloak integration

2. **keycloak** (Port 8084): Authentication service
   - OAuth2/OIDC provider
   - User and role management

3. **postgres** (Port 5432): Database
   - Stores registration logs, geofence deployments, vendor/user registration limits

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

   **Keycloak Configuration:**
   - `KEYCLOAK_ENDPOINT`: Keycloak server URL (default: `http://localhost:8084`)
   - `KEYCLOAK_REALM`: Realm name (default: `v2x-app`)
   - `KEYCLOAK_CLIENT_NAME`: Client ID (default: `v2x-app-api`)
   - `KEYCLOAK_CLIENT_SECRET`: Client secret (generate a secure 32-character string)

   **ETX Configuration:**
   - `ETX_ENABLED`: Enable ETX integration (default: `true`)
   - `ETX_ENDPOINT`: ETX API endpoint (default: `https://imp.thingspace.verizon.com`)
   - `ETX_VENDOR_ID`: Vendor ID for registrations
   - `ETX_VENDOR_USERNAME`: ETX vendor username
   - `ETX_VENDOR_PASSWORD`: ETX vendor password
   - `ETX_DEPOSITOR_VENDOR_ID`: Separate vendor ID for depositor role (defaults to `ETX_VENDOR_ID`)

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

   **Registration Limits:**
   - `ETX_VENDOR_REGISTRATION_LIMIT`: Max registrations per vendor (default: `50`)
   - `ETX_VENDOR_USER_REGISTRATION_LIMIT`: Max registrations per user (default: `5`)
   - `ETX_DEPOSITOR_VENDOR_REGISTRATION_LIMIT`: Max registrations for depositor vendor (default: `10`)
   - `ETX_DEPOSITOR_VENDOR_USER_REGISTRATION_LIMIT`: Max registrations per depositor user (default: `3`)

   **Deployment Mode:**
   - `DEPLOYMENT_MODE`: `ETX_CONFIGURATION_API` (default) or `GEOFENCE_MQTT`

### Docker Compose Profiles

Control which services start using profiles:
- `postgres`: PostgreSQL only
- `keycloak`: Keycloak + PostgreSQL
- `v2x-app-api`: API + PostgreSQL
- `all`: All services (default)

Example:
```bash
COMPOSE_PROFILES=v2x-app-api docker compose up
```

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
   - API: `http://localhost:8080`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - API Docs: `http://localhost:8080/api-docs`
   - Keycloak: `http://localhost:8084`

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
- Geohash precision configuration
- Expiration grace periods

### Path Management
- GeoJSON-like path storage
- Timestamp and metadata tracking
- CRUD operations for path data

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
