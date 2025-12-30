# MQTT mTLS-Based Routing Implementation

## Overview

This document describes the implementation of mTLS-based MQTT routing using MQTT client IDs instead of BSM device IDs. The system now routes messages based on registered MQTT client IDs stored in PostgreSQL.

## Architecture Changes

### 1. MQTT Device Registration (v2x-app-api)

**New REST Endpoint**: `/prd/v2/mqtt/devices/location`

- **POST**: Register or update MQTT device location
  ```json
  {
    "mqttClientId": "device-001",
    "latitude": 34.0554976,
    "longitude": -84.2760438,
    "elevation": 0.0,
    "heading": 90.0,
    "speed": 20.0
  }
  ```

- **GET**: Get device location by MQTT client ID
- **DELETE**: Deactivate device (on disconnect)

**Database Entity**: `MqttDeviceLocation`
- Stores MQTT client ID, location, geohash, and metadata
- Indexed on `mqtt_client_id`, `geohash`, and `last_updated`

### 2. Geo Router Service Updates

**Key Changes**:
- Extracts MQTT client ID from MQTT v5 user properties (instead of BSM device ID)
- Queries PostgreSQL database for device locations
- Routes messages to registered MQTT client IDs

**Components**:
- `PostgresDeviceLocationCache`: Queries PostgreSQL for device locations
- `DatabaseConfig`: Configures JdbcTemplate connection to v2x-app-api database
- Updated `GeoRouterService`: Uses MQTT client IDs for routing

### 3. MQTT Client ID Extraction

The geo-router extracts MQTT client ID from MQTT v5 user properties:
- User property key: `mqttClientId` or `clientId`
- Clients must include their MQTT client ID in user properties when publishing

**Alternative**: With mTLS, the client ID can be extracted from the certificate's CN or SAN.

## Configuration

### Geo Router Service (`application.yml`)

```yaml
geo-routing:
  database:
    url: jdbc:postgresql://postgres:5432/v2x_app_db
    username: admin_user
    password: ${GEO_ROUTING_DATABASE_PASSWORD}
```

### Environment Variables

```bash
GEO_ROUTING_DATABASE_URL=jdbc:postgresql://postgres:5432/v2x_app_db
GEO_ROUTING_DATABASE_USERNAME=admin_user
GEO_ROUTING_DATABASE_PASSWORD=your_password
```

## mTLS Configuration (TODO)

### VerneMQ mTLS Setup

1. **Generate Certificates**:
   - CA certificate for VerneMQ
   - Server certificate for VerneMQ broker
   - Client certificates for each MQTT client

2. **VerneMQ Configuration**:
   ```bash
   DOCKER_VERNEMQ_LISTENER__SSL__DEFAULT=8883
   DOCKER_VERNEMQ_LISTENER__SSL__DEFAULT__CAFILE=/etc/ssl/ca.crt
   DOCKER_VERNEMQ_LISTENER__SSL__DEFAULT__CERTFILE=/etc/ssl/server.crt
   DOCKER_VERNEMQ_LISTENER__SSL__DEFAULT__KEYFILE=/etc/ssl/server.key
   DOCKER_VERNEMQ_LISTENER__SSL__DEFAULT__REQUIRE_CERTIFICATE=true
   ```

3. **Client Certificate Mapping**:
   - Extract CN or SAN from client certificate
   - Use as MQTT client ID for routing

### MQTT Client mTLS Configuration

**Java (Eclipse Paho)**:
```java
MqttConnectionOptions options = new MqttConnectionOptions();
SSLContext sslContext = SSLContext.getInstance("TLS");
// Load client certificate and key
options.setSocketFactory(sslContext.getSocketFactory());
```

**Python (paho-mqtt)**:
```python
client.tls_set(
    ca_certs="ca.crt",
    certfile="client.crt",
    keyfile="client.key",
    cert_reqs=ssl.CERT_REQUIRED
)
```

## Usage Flow

1. **Device Registration**:
   ```bash
   curl -X POST http://localhost:8080/prd/v2/mqtt/devices/location \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "mqttClientId": "device-001",
       "latitude": 34.0554976,
       "longitude": -84.2760438
     }'
   ```

2. **Publish BSM with MQTT Client ID**:
   - Include `mqttClientId` in MQTT v5 user properties
   - Publish to `v2x/bsm/publish`

3. **Geo Router Processing**:
   - Extracts MQTT client ID from user properties
   - Decodes BSM to get location
   - Queries database for devices in relevant geohashes
   - Routes message to `v2x/client/{mqttClientId}/messages`

4. **Client Subscription**:
   - Subscribe to `v2x/client/{yourMqttClientId}/messages`
   - Receive routed messages

## Database Schema

```sql
CREATE TABLE mqtt_device_locations (
    id BIGSERIAL PRIMARY KEY,
    mqtt_client_id VARCHAR(255) NOT NULL UNIQUE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    geohash VARCHAR(20),
    elevation DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_updated TIMESTAMP NOT NULL DEFAULT NOW(),
    last_connected TIMESTAMP,
    vendor_id VARCHAR(255),
    registered_by VARCHAR(255)
);

CREATE INDEX idx_mqtt_client_id ON mqtt_device_locations(mqtt_client_id);
CREATE INDEX idx_geohash ON mqtt_device_locations(geohash);
CREATE INDEX idx_last_updated ON mqtt_device_locations(last_updated);
```

## Migration Notes

1. **Existing BSM-based routing** is replaced with MQTT client ID-based routing
2. **Clients must register** via REST API before receiving routed messages
3. **MQTT client IDs** must be included in user properties when publishing
4. **PostgreSQL database** must be accessible from geo-router service

## Next Steps

1. Implement mTLS certificate generation scripts
2. Configure VerneMQ for mTLS
3. Update MQTT client libraries to use mTLS
4. Extract client ID from mTLS certificates (alternative to user properties)
5. Update test scripts to use mTLS and include MQTT client ID in user properties



