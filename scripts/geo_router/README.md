# V2X Geo Router Test Script

Test script for debugging and validating the V2X MQTT geo routing system.

## Prerequisites

1. **Python 3.8+**
2. **MQTT Broker** (VerneMQ) running and accessible
3. **Geo Router Service** running and connected to the broker
4. **J2735 Serializer API** running (for BSM encoding)
5. **v2x-app-api** running (for device registration)

## Installation

Use a virtual python environment:

```bash
python3 -m venv venv
```

Activate the virtual environment:

```sh
source venv/bin/activate
```

Install required Python packages:

```bash
pip3 install -r requirements.txt
```

## Configuration

1. **Copy the sample environment file**:
   ```bash
   cp scripts/geo_router/sample.env scripts/geo_router/.env
   ```

2. **Edit `.env` file** with your configuration:
   ```bash
   # MQTT Broker Configuration
   MQTT_BROKER_HOST=localhost
   MQTT_BROKER_PORT=1883

   # V2X API Configuration
   V2X_API_URL=http://localhost:8080
   V2X_API_USERNAME=your-username
   V2X_API_PASSWORD=your-password

   # Serializer API Configuration
   SERIALIZER_API_URL=http://localhost:4000
   ```

   **Note**: The `.env` file is git-ignored and should not be committed. Use `sample.env` as a template.
   
   **Authentication**: The scripts will automatically authenticate with the V2X API using your username and password to obtain an access token. The token is obtained from the `/auth/token` endpoint before making any authenticated API calls.

## Usage

### Basic Test

Test with default coordinates (Atlanta area):

```bash
python3 scripts/geo_router/test_geo_router.py
```

### Custom Coordinates

Test with specific latitude/longitude:

```bash
python3 scripts/geo_router/test_geo_router.py --lat 37.7749 --lon -122.4194
```

### Standalone Subscriber

For latency and routing testing, use the standalone subscriber in a separate terminal. The subscriber automatically publishes its location on startup so the geo router is aware of it:

```bash
# Subscribe to a specific device's egress topic (publishes location automatically)
python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438

# Subscribe with verbose output
python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --verbose

# Subscribe for a specific duration (e.g., 60 seconds)
python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --duration 60

# Skip location publish (not recommended - geo router won't route to you)
python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --no-location-publish
```

**Note**: The `--lat` and `--lon` arguments are required (unless `--no-location-publish` is used) so the geo router knows where your device is located and can route messages to you.

### Standalone Publisher

Publish BSM messages for testing:

```bash
# Publish a single BSM message
python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438

# Publish multiple messages at 10 msg/s
python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --num-messages 100 --rate 10

# Load test: 1000 messages at 100 msg/s
python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --num-messages 1000 --rate 100
```

**Testing Workflow:**
1. Start the subscriber in one terminal: 
   ```bash
   python scripts/geo_router/subscriber.py --device-id SUBSCRIBER_DEVICE_ID --lat 34.0554976 --lon -84.2760438 --verbose
   ```
2. Run the publisher in another terminal:
   ```bash
   python scripts/geo_router/publisher.py --device-id PUBLISHER_DEVICE_ID --lat 34.0554976 --lon -84.2760438 --num-messages 1000 --rate 100
   ```
3. The subscriber will receive messages routed to its device ID and display statistics

### Multiple Messages

Publish multiple BSM messages:

```bash
python scripts/geo_router/test_geo_router.py --num-messages 5
```

### Custom Broker

Test against a different MQTT broker:

```bash
python scripts/geo_router/test_geo_router.py --broker-host 192.168.1.100 --broker-port 1883
```

### Environment Variables

You can also use environment variables:

```bash
export MQTT_BROKER_HOST=localhost
export MQTT_BROKER_PORT=1883
export SERIALIZER_API_URL=http://localhost:4000
python scripts/test_geo_router.py
```

## Command Line Options

```
--lat FLOAT              Latitude for BSM messages (default: 34.0554976)
--lon FLOAT              Longitude for BSM messages (default: -84.2760438)
--num-messages INT       Number of BSM messages to publish (default: 1)
--delay FLOAT            Delay in seconds to wait for routing (default: 3.0)
--broker-host STRING     MQTT broker host
--broker-port INT        MQTT broker port
--serializer-url STRING  Serializer API URL
```

## How It Works

1. **Creates BSM Messages**: Generates BSM JSON structures with specified coordinates
2. **Encodes to ASN.1**: Converts BSM JSON to ASN.1 UPER hex using the serializer API
3. **Publishes to Broker**: Sends BSM messages to `v2x/bsm/publish` topic
4. **Subscribes to Routing**: Listens on `v2x/georelevance/+/+/+/+/+/+/+/BSM/+` pattern
5. **Validates Routing**: Checks if messages are received on geo-relevance topics

## Expected Output

On success, you should see:

```
[Publisher] Published BSM #1 to v2x/bsm/publish
[Subscriber] Received message #1
  Topic: v2x/georelevance/9/q/8/y/y/k/7/BSM/+
  Payload size: 123 bytes

✅ SUCCESS: Geo router is working!
```

## Troubleshooting

### No Messages Received

- Check that geo_router-service is running
- Verify MQTT broker is accessible
- Check geo router logs for errors
- Ensure geohash precision matches subscription pattern

### Serializer API Not Available

- Start the J2735 serializer API service
- Verify it's running on port 4000 (or update SERIALIZER_API_URL)
- Check API health endpoint

### MQTT Connection Issues

- Verify broker host and port are correct
- Check firewall/network settings
- Ensure MQTT v5 is enabled on broker

## Example Test Scenarios

### Test Different Geographic Areas

```bash
# San Francisco
python scripts/geo_router/test_geo_router.py --lat 37.7749 --lon -122.4194

# New York
python scripts/geo_router/test_geo_router.py --lat 40.7128 --lon -74.0060

# London
python scripts/geo_router/test_geo_router.py --lat 51.5074 --lon -0.1278
```

### Stress Test

```bash
# Publish 100 messages
python scripts/geo_router/test_geo_router.py --num-messages 100 --delay 10
```

