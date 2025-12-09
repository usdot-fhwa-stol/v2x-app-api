# MQTT Router Service Testing

This directory contains a Python test script to verify the MQTT router service functionality.

## Prerequisites

create venv

```bash
python3 -m venv venv
```


use venv

```bash
source  venv/bin/activate
```

Install the required Python packages:

```bash
pip install -r requirements.txt
```

Or using pip3:

```bash
pip3 install -r requirements.txt
```

## Running the Tests

### Basic Usage

```bash
python3 test_router.py
```

### With Custom Configuration

```bash
python3 test_router.py \
  --mqtt-host localhost \
  --mqtt-port 1883 \
  --router-api http://localhost:8081 \
  --client-id-prefix my-test-client
```

## Test Coverage

The test script verifies:

1. **Router Health Check**
   - Verifies the router service is running
   - Checks MQTT broker connection status

2. **GeoRelevance to Regional Routing**
   - Publishes messages to GeoRelevance topics
   - Verifies messages are correctly routed to Regional topics
   - Tests message type filtering (only RSA, TIM, SPAT, MAP, BSM, SDSM route to regional)
   - Verifies PSM and TUM messages are NOT routed to regional

3. **Client Subscription API**
   - Tests REST API endpoints for subscription management
   - Registers a client subscription
   - Retrieves client subscriptions
   - Unregisters a subscription
   - Verifies subscription removal

4. **Subscribed Client Message Delivery**
   - Registers a client subscription via REST API
   - Publishes a message to a GeoRelevance topic
   - Verifies the subscribed client receives the message

## Expected Output

```
============================================================
MQTT Router Service Test Suite
============================================================
[2024-01-01 12:00:00] [INFO] Testing router service health...
[2024-01-01 12:00:00] [INFO] Router health: {...}
[2024-01-01 12:00:00] [INFO] ✓ Router service is healthy and MQTT is connected

=== Testing GeoRelevance to Regional Routing ===
[2024-01-01 12:00:01] [INFO] Subscribed to regional topic: v2x/1/Regional/+/+/+/+/+/+/+/+/+/+/Public/+
[2024-01-01 12:00:01] [INFO] Publishing to GeoRelevance: v2x/1/GeoRelevance/OBU/Vehicle/Public/BSM
[2024-01-01 12:00:03] [SUCCESS] ✓ Message BSM correctly routed to regional topic
...

============================================================
Test Summary
============================================================
Health Check: ✓ PASSED
Routing Test: ✓ PASSED
Subscription API: ✓ PASSED
Message Delivery: ✓ PASSED

Overall: ✓ ALL TESTS PASSED
```

## Troubleshooting

### Router Service Not Running

If the health check fails, ensure:
- The router service is running: `docker compose up mqtt-router`
- The router API is accessible at the configured URL
- MQTT broker (Mosquitto) is running and connected

### MQTT Connection Issues

If MQTT tests fail:
- Verify Mosquitto is running: `docker compose ps mosquitto`
- Check MQTT broker is accessible on the configured host/port
- Ensure firewall allows MQTT connections (port 1883)

### No Messages Received

If messages aren't being received:
- Check router service logs: `docker compose logs mqtt-router`
- Verify the router is subscribed to GeoRelevance topics
- Check MQTT broker logs: `docker compose logs mosquitto`
- Ensure message payloads are valid

## Test Script Options

- `--mqtt-host`: MQTT broker hostname (default: localhost)
- `--mqtt-port`: MQTT broker port (default: 1883)
- `--router-api`: Router service API URL (default: http://localhost:8081)
- `--client-id-prefix`: Prefix for test client IDs (default: test-client)

