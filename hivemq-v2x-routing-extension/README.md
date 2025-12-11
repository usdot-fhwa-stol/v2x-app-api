# V2X Geohash Routing HiveMQ Extension

A HiveMQ extension that routes J2735 BSM messages based on geohash. Intercepts BSM messages published to ingress topics, extracts location data, computes geohash and 3x3 neighbors, and re-publishes to geohash-based topics. Also rewrites egress subscriptions to geohash topics.

## Features

- **BSM Location Extraction**: Extracts latitude/longitude from BSM messages (ASN.1 or JSON)
- **Geohash Routing**: Computes level-7 geohash and 3x3 grid expansion
- **Dynamic Topic Routing**: Routes messages to `v2x/1/geo/{char1}/{char2}/{char3}/{char4}/{char5}/{char6}/{char7}/bsm`
- **Subscription Rewriting**: Rewrites egress subscriptions to geohash topics
- **Non-blocking Processing**: Asynchronous message processing to avoid blocking the broker

## Architecture

```
[CV MEC] → Publish BSM to /v2x/1/ingress/bsm
                ↓
[HiveMQ Publish Interceptor]
    ↓
[BSM Decoder] → Extract lat/lon from BSM (ASN.1/JSON)
    ↓
[Geohash Service] → Compute geohash + 3x3 neighbors
    ↓
[Re-publish] → /v2x/1/geo/{char1}/{char2}/.../{char7}/bsm

[CV MEC] → Subscribe to /v2x/1/egress/{messageType}/#
                ↓
[HiveMQ Subscription Interceptor]
    ↓
[Rewrite] → /v2x/1/geo/+/+/+/+/+/+/+/{messageType}
```

## Building

### Prerequisites

- Java 22
- Gradle 8.x
- HiveMQ 4.x

### Build Steps

1. **Build JPO ASN dependencies** (if using ASN.1 decoding):
   ```bash
   cd jpo-asn-pojos/jpo-asn-runtime
   ./gradlew build
   cd ../jpo-asn-j2735-2024
   ./gradlew build
   ```

2. **Build the extension**:
   ```bash
   cd hivemq-v2x-routing-extension
   ./gradlew clean build
   ```

3. **Package extension**:
   ```bash
   ./gradlew buildExtension
   ```

   This creates `build/distributions/v2x-geohash-routing-extension-1.0.0.zip`

## Installation

1. Extract the extension ZIP file
2. Copy the `extension` directory to HiveMQ's extensions folder:
   ```
   ${HIVEMQ_HOME}/extensions/v2x-geohash-routing/
   ```
3. Restart HiveMQ

## Configuration

The extension automatically:
- Intercepts publishes to topics matching `v2x/1/GeoRelevance/**`
- Routes eligible messages to Regional topics
- Uses level-7 geohash precision
- Expands to 3x3 grid (9 geohashes total)

## Topic Structure

### Ingress Topics (CV MEC Publishing)
```
/v2x/1/ingress/bsm
```
CV MEC publishes BSM messages with position information to this topic.

### Geohash Topics (Broker Routing)
```
/v2x/1/geo/{char1}/{char2}/{char3}/{char4}/{char5}/{char6}/{char7}/bsm
```
Broker routes BSM messages to geohash-based topics. Each `{charN}` is one character of the 7-character level-7 geohash.

### Egress Topics (CV MEC Subscribing)
```
/v2x/1/egress/{messageType}/#
```
CV MEC subscribes to egress topics. The extension automatically rewrites these to:
```
/v2x/1/geo/+/+/+/+/+/+/+/{messageType}
```

## Supported Message Types

- **BSM** (Basic Safety Message) - Extracts location from `coreData.lat/long`
  - Only BSM is used for location extraction
  - Other message types (SPAT, TIM, etc.) can be subscribed via egress topics

## Dependencies

- **HiveMQ Extension SDK 4.47.0**: Core extension framework
- **JTS Core 1.19.0**: Spatial operations (for future enhancements)
- **Geohash 1.4.0**: Geohash encoding/decoding
- **Jackson 2.17.2**: JSON processing
- **JPO ASN Runtime**: ASN.1 J2735 message classes (for binary decoding)
- **j2735-2024-ffm-lib**: FFM-based ASN.1 codec library
- **Native Library**: `libasnapplication.so` - C library for ASN.1 encoding/decoding (included in extension)

## Message Format Support

Currently supports:
- ✅ JSON-encoded BSM messages
- ✅ ASN.1 UPER binary BSM messages (requires native library `libasnapplication.so`)

The native library `libasnapplication.so` is included in the extension package and will be automatically extracted from the JAR at runtime if needed. The library is located at `src/main/resources/j2735ffm/libasnapplication.so` in the source code.

## Logging

The extension uses SLF4J logging. Logs are written to HiveMQ's log directory.

Log levels:
- `INFO`: Extension lifecycle events
- `DEBUG`: Message processing details
- `WARN`: Missing location data or unsupported formats
- `ERROR`: Processing failures

## Performance Considerations

- Message processing is asynchronous to avoid blocking the broker
- Geohash computation is lightweight
- 3x3 grid expansion results in 9 topic publications per message
- Consider message volume when planning broker capacity

## Native Library

The extension includes the native C library `libasnapplication.so` from the `j2735-ffm-java` project. This library enables decoding of ASN.1 UPER binary messages.

**Location in source**: `src/main/resources/j2735ffm/libasnapplication.so`

**Runtime behavior**: 
- The library is packaged in the extension JAR
- At runtime, if the library is in a JAR, it will be automatically extracted to a temporary file
- The temporary file is deleted when the JVM exits
- The library must be present for ASN.1 binary decoding to work

**Building with the library**:
The library should be copied to `src/main/resources/j2735ffm/` before building:
```bash
cp j2735-ffm-java/lib/libasnapplication.so hivemq-v2x-routing-extension/src/main/resources/j2735ffm/
```

## Future Enhancements

- [ ] Configurable geohash precision
- [ ] Configurable grid size (3x3, 5x5, etc.)
- [ ] Subscription interception for dynamic geohash-based subscriptions
- [ ] Metrics and monitoring integration
- [ ] Message filtering based on geofence boundaries

## License

See LICENSE file for details.

