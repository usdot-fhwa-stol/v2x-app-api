# Testing MQTT Client ID-Based Routing

## Overview

This guide explains how to test the new MQTT client ID-based routing functionality.

## Prerequisites

1. **Services Running**:
   - VerneMQ MQTT broker (port 1883)
   - v2x-app-api REST service (port 8080)
   - geo-router-service (port 8081)
   - PostgreSQL database
   - Serializer API (port 4000) - for BSM encoding

2. **Authentication Credentials**:
   Configure in `.env` file:
   ```bash
   V2X_API_USERNAME=your-username
   V2X_API_PASSWORD=your-password
   ```
   
   The scripts will automatically authenticate and obtain an access token from the `/auth/token` endpoint.

## Test Scenario: Two Devices

### Step 1: Start Subscriber (Device 1)

Terminal 1 - Device 1 subscribes and registers location:

```bash
python scripts/geo_router/subscriber.py \
  --device-id 6F9E7F2B \
  --mqtt-client-id device-001 \
  --lat 34.0554976 \
  --lon -84.2760438 \
  --verbose
```

Expected output:
- Device location registered via REST API
- Connected to MQTT broker
- Subscribed to `v2x/client/device-001/messages`
- Waiting for messages...

### Step 2: Start Publisher (Device 2)

Terminal 2 - Device 2 publishes BSMs:

```bash
python scripts/geo_router/publisher.py \
  --device-id A1B2C3D4 \
  --mqtt-client-id device-002 \
  --lat 34.0554976 \
  --lon -84.2760438 \
  --num-messages 5 \
  --rate 1
```

Expected output:
- Device location registered via REST API
- Connected to MQTT broker
- Publishing BSMs with MQTT client ID in user properties
- Messages published successfully

### Step 3: Verify Routing

In Terminal 1 (Subscriber), you should see:
- Messages received on `v2x/client/device-001/messages`
- BSM payloads from device-002
- Message statistics

## Test Scenario: Same Location, Different MQTT Client IDs

This tests that routing works based on MQTT client ID, not BSM device ID:

1. **Subscriber 1** (MQTT client ID: `subscriber-1`):
   ```bash
   python scripts/geo_router/subscriber.py \
     --device-id SUB1 \
     --mqtt-client-id subscriber-1 \
     --lat 34.0554976 \
     --lon -84.2760438 \
     --verbose
   ```

2. **Subscriber 2** (MQTT client ID: `subscriber-2`):
   ```bash
   python scripts/geo_router/subscriber.py \
     --device-id SUB2 \
     --mqtt-client-id subscriber-2 \
     --lat 34.0554976 \
     --lon -84.2760438 \
     --verbose
   ```

3. **Publisher** (MQTT client ID: `publisher-1`):
   ```bash
   python scripts/geo_router/publisher.py \
     --device-id PUB1 \
     --mqtt-client-id publisher-1 \
     --lat 34.0554976 \
     --lon -84.2760438 \
     --num-messages 10
   ```

Expected: Both subscribers should receive messages from `publisher-1`.

## Troubleshooting

### Issue: "Device location registration failed"

**Solution**:
- Check that `V2X_API_USERNAME` and `V2X_API_PASSWORD` are set correctly in `.env`
- Verify v2x-app-api is running and accessible
- Check that authentication succeeds (look for "Authentication successful" message)
- Check API logs for authentication errors
- Verify the `/auth/token` endpoint is accessible

### Issue: "No messages received"

**Possible Causes**:
1. **Device not registered**: Check that device location was registered successfully
2. **Different locations**: Devices must be in the same geohash area
3. **MQTT client ID mismatch**: Ensure subscriber's `--mqtt-client-id` matches what's registered
4. **Geo-router not running**: Check geo-router-service logs

### Issue: "MQTT client ID not found in user properties"

**Solution**:
- This is a warning, not an error
- The geo-router will attempt to extract MQTT client ID from user properties
- If user properties aren't available, it may fall back to connection client ID
- Ensure you're using MQTT v5 and paho-mqtt 2.0+

### Debugging Steps

1. **Check Device Registration**:
   ```bash
   curl -H "Authorization: Bearer $V2X_API_TOKEN" \
     http://localhost:8080/prd/v2/mqtt/devices/location/device-001
   ```

2. **Check Geo-Router Logs**:
   - Look for "Routing BSM from MQTT client X to Y other clients"
   - Check for "MQTT client ID not found in message user properties"

3. **Check MQTT Broker**:
   - Verify messages are being published to `v2x/bsm/publish`
   - Verify messages are being published to `v2x/client/{mqttClientId}/messages`

## Environment Variables

Configure in `.env` file (see `sample.env` for template):

```bash
# MQTT Broker
MQTT_BROKER_HOST=localhost
MQTT_BROKER_PORT=1883

# V2X API
V2X_API_URL=http://localhost:8080
V2X_API_USERNAME=your-username
V2X_API_PASSWORD=your-password

# Serializer API
SERIALIZER_API_URL=http://localhost:4000
```

**Note**: The scripts use `python-dotenv` to load these from the `.env` file automatically. You can also override them via command-line arguments or system environment variables.

## Notes

- **MQTT Client ID vs Device ID**: 
  - Device ID is used in BSM message content
  - MQTT Client ID is used for routing and subscription topics
  - They can be different (useful for testing)

- **User Properties**: 
  - MQTT v5 user properties are used to include MQTT client ID in published messages
  - If user properties aren't supported, the connection client ID is used as fallback

- **Location Registration**:
  - Devices must register their location before receiving routed messages
  - Location updates can be done via REST API or by republishing with new location

