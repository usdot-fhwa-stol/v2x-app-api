# V2X Geo Router Service

A Spring Boot microservice that provides geospatial routing for V2X MQTT messages. The service receives BSM (Basic Safety Message) messages via MQTT, decodes them to extract geographic coordinates, calculates relevant geohash cells, and routes messages to subscribers based on geographic relevance.

## Architecture

```
Devices → VerneMQ Broker → Geo Router Service → VerneMQ Broker → Devices
         (publish BSM)    (decode & route)     (publish routed)
```

All devices and the geo router service connect to a single MQTT broker (VerneMQ). The geo router subscribes to incoming BSM messages and publishes routed messages to the same broker.

### Components

1. **MQTT Client Service**: Manages connection to a single MQTT broker (both subscribe and publish)
2. **BSM Decoder Service**: Decodes ASN.1 UPER encoded BSM messages and extracts lat/lon coordinates
3. **Geohash Router**: Calculates relevant geohash cells for message routing
4. **Subscription Manager**: Tracks subscriber geographic interests and matches messages to subscribers
5. **Geo Router Service**: Core orchestration service that coordinates message processing and routing

## Features

- **BSM Decoding**: Uses j2735-ffm-java to decode ASN.1 UPER encoded messages
- **Geohash-based Routing**: Calculates relevant geohash cells with configurable precision and neighbor radius
- **Subscriber Management**: Tracks subscriber geographic interests and message type preferences
- **Asynchronous Processing**: Non-blocking message routing for high throughput
- **MQTT Integration**: Supports both ingress (message reception) and egress (message publishing) brokers

## Configuration

### MQTT Configuration

```yaml
mqtt:
  broker-url: tcp://localhost:1883
  client-id: geo-router-service
  subscribe-topic: v2x/bsm/publish
  publish-topic-pattern: v2x/georelevance/{geohash_path}/{message_type}/+
  qos: 1
```

### Geospatial Routing Configuration

```yaml
geo-routing:
  geohash:
    precision: 7          # Geohash precision (characters)
    neighbor-radius: 1    # Number of neighbor cells to include
  routing:
    enabled: true
    max-subscribers-per-message: 1000
    message-ttl-seconds: 10
```

### J2735 Codec Configuration

```yaml
j2735:
  codec:
    library-path: /usr/lib/libasnapplication.so
    text-buffer-size: 262144
    uper-buffer-size: 8192
    error-buffer-size: 256
```

## Message Flow

### Publishing BSM Messages

1. Devices publish BSM messages (ASN.1 UPER encoded) to the ingress broker:
   - **Topic**: `v2x/bsm/publish`
   - **Payload**: Raw ASN.1 UPER binary bytes

### Subscribing to Geographically Relevant Messages

1. Subscribers subscribe to the broker using geohash-based topics with hierarchical path format:
   - **Topic Pattern**: `v2x/georelevance/{geohash_path}/{message_type}/+`
   - **Example**: `v2x/georelevance/9/q/8/y/y/k/7/BSM/+`
   - **Wildcard Examples**:
     - `v2x/georelevance/9/+/+/+/+/+/+/BSM/+` - All messages starting with '9' (coarse area)
     - `v2x/georelevance/9/q/+/+/+/+/+/BSM/+` - More specific area
     - `v2x/georelevance/9/q/8/y/y/k/7/BSM/+` - Exact geohash (most precise)

2. The geo router service:
   - Receives BSM messages from ingress broker
   - Decodes BSM to extract lat/lon coordinates
   - Calculates relevant geohashes (primary + neighbors)
   - Matches geohashes to subscriber interests
   - Publishes messages to matching subscribers' topics

## Building

### Prerequisites

- Java 22+
- Gradle 8+
- Native library: `libasnapplication.so` (Linux) or `asnapplication.dll` (Windows)

### Build Commands

```bash
cd geo-router-service
./gradlew clean build
```

## Running

### Local Development

1. Ensure NanoMQ broker is running on port 1883
2. Ensure native library is available at `/usr/lib/libasnapplication.so`
3. Run the application:

```bash
./gradlew bootRun
```

### Docker

The service can be run using Docker Compose (see main `docker-compose.yml`):

```bash
docker compose --profile geo-router up --build
```

## API Endpoints

### Health Check

- `GET /actuator/health` - Service health status
- `GET /actuator/info` - Service information
- `GET /actuator/metrics` - Prometheus metrics

## Testing

Run unit tests:

```bash
./gradlew test
```

## Dependencies

- **Spring Boot 3.4.5**: Application framework
- **Eclipse Paho MQTT Client**: MQTT connectivity
- **j2735-2024-ffm-lib**: J2735 message codec
- **jpo-asn-j2735-2024**: J2735 POJO library
- **ch.hsr:geohash**: Geohash calculations

## Troubleshooting

### Native Library Not Found

Ensure `libasnapplication.so` is available:
- **Linux/WSL**: Copy to `/usr/lib/` or set `LD_LIBRARY_PATH`
- **Docker**: Mount volume or copy in Dockerfile

### MQTT Connection Issues

- Verify broker URLs are correct
- Check network connectivity between services
- Review MQTT broker logs

### Message Decoding Failures

- Verify messages are valid ASN.1 UPER encoded BSM messages
- Check codec buffer sizes are sufficient
- Review application logs for detailed error messages

## License

See LICENSE file for details.

