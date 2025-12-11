# HiveMQ V2X Extension Test Script

This directory contains test scripts for the HiveMQ V2X Geohash Routing Extension.

## Prerequisites

1. Python 3.8+
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

## Running Tests

### Using Docker Compose

1. **Start HiveMQ with the extension**:
   ```bash
   docker-compose --profile hivemq up -d
   ```

2. **Wait for HiveMQ to start** (check logs):
   ```bash
   docker-compose logs -f hivemq
   ```

3. **Run the test script**:
   ```bash
   python3 test_hivemq_extension.py --mqtt-host localhost --mqtt-port 1883
   ```

### Manual Testing

If HiveMQ is running elsewhere:

```bash
python3 test_hivemq_extension.py --mqtt-host <host> --mqtt-port <port>
```

## Test Coverage

The test script verifies:

1. **BSM Ingress Routing**: 
   - Publishes BSM messages to `/v2x/1/ingress/bsm`
   - Verifies messages are routed to geohash topics: `/v2x/1/geo/{char1}/{char2}/.../{char7}/bsm`
   - Checks that messages appear on the correct geohash or neighbor geohashes (3x3 grid)

2. **Subscription Rewriting**:
   - Subscribes to `/v2x/1/egress/bsm/#`
   - Verifies the subscription is rewritten to `/v2x/1/geo/+/+/+/+/+/+/+/bsm`
   - Confirms messages are received on geohash topics

3. **Message Type Filtering**:
   - Verifies only BSM messages are processed
   - Non-BSM messages to ingress topics should be ignored

## Expected Output

```
[2024-01-01 12:00:00] [INFO] ============================================================
[2024-01-01 12:00:00] [INFO] HiveMQ V2X Geohash Routing Extension Test Suite
[2024-01-01 12:00:00] [INFO] ============================================================

[2024-01-01 12:00:01] [INFO] === Testing BSM Ingress to Geohash Routing ===
[2024-01-01 12:00:02] [SUCCESS] ✓ Message from Denver correctly routed to geohash topic
...

[2024-01-01 12:00:10] [INFO] ============================================================
[2024-01-01 12:00:10] [INFO] Test Summary
[2024-01-01 12:00:10] [INFO] ============================================================
[2024-01-01 12:00:10] [INFO] BSM Ingress Routing: ✓ PASSED
[2024-01-01 12:00:10] [INFO] Subscription Rewriting: ✓ PASSED
[2024-01-01 12:00:10] [INFO] Message Type Filtering: ✓ PASSED
[2024-01-01 12:00:10] [INFO] Overall: ✓ ALL TESTS PASSED
```

## Troubleshooting

- **Connection refused**: Ensure HiveMQ is running and accessible on the specified host/port
- **No messages received**: Check HiveMQ logs for extension errors
- **Extension not loading**: Verify the extension ZIP was built and installed correctly


