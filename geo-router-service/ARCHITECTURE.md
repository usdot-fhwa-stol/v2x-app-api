# Geo Router Architecture: Direct Client Routing

## Overview

The Geo Router Service now implements a **direct client routing** architecture where:

1. **Clients** (vehicles/devices) only deal with simple ingress/egress topics
2. **TMC Applications** distribute messages using geohash-filtered topics
3. **Geo Router** acts as a smart proxy that routes messages directly to relevant clients based on cached device locations

## Architecture Flow

```
┌─────────────┐
│   Clients   │
│ (Vehicles)  │
└──────┬──────┘
       │ Publish BSM
       │ Topic: v2x/bsm/publish
       ▼
┌─────────────────────────────────────────────────────────────┐
│                    NanoMQ Broker                             │
└──────┬──────────────────────────────────────────────────────┘
       │
       │ Subscribe to ingress
       ▼
┌─────────────────────────────────────────────────────────────┐
│              Geo Router Service                              │
│                                                               │
│  1. Receives BSMs from ingress topic                         │
│     → Decodes BSM                                            │
│     → Extracts device ID and location                        │
│     → Caches device location                                 │
│                                                               │
│  2. Subscribes to geohash-filtered topics                   │
│     Topic: v2x/georelevance/+/+/+/+/+/+/+/BSM              │
│     (Messages from TMC apps)                                 │
│                                                               │
│  3. For each geohash-filtered message:                      │
│     → Decodes message to extract coordinates                 │
│     → Calculates relevant geohashes                          │
│     → Finds devices in those geohashes (from cache)          │
│     → Publishes directly to client egress topics             │
│                                                               │
└──────┬──────────────────────────────────────────────────────┘
       │ Publish to client topics
       │ Topic: v2x/client/{deviceId}/messages
       ▼
┌─────────────────────────────────────────────────────────────┐
│                    NanoMQ Broker                             │
└──────┬──────────────────────────────────────────────────────┘
       │
       │ Subscribe to egress
       ▼
┌─────────────┐
│   Clients   │
│ (Vehicles)  │
└─────────────┘
```

## Topic Structure

### Ingress Topic (Client → Geo Router)
- **Topic**: `v2x/bsm/publish`
- **Publisher**: Clients (vehicles/devices)
- **Subscriber**: Geo Router Service
- **Purpose**: Clients publish their BSM messages here

### Geohash-Filtered Topics (TMC Apps → Geo Router)
- **Topic Pattern**: `v2x/georelevance/+/+/+/+/+/+/+/BSM`
- **Publisher**: TMC Applications
- **Subscriber**: Geo Router Service
- **Purpose**: TMC apps distribute messages using geohash-based filtering

### Client Egress Topics (Geo Router → Clients)
- **Topic Pattern**: `v2x/client/{deviceId}/messages`
- **Publisher**: Geo Router Service
- **Subscriber**: Clients (vehicles/devices)
- **Purpose**: Geo router publishes relevant messages directly to each client

## Key Components

### 1. Device Location Cache

The geo router maintains a cache of device locations extracted from BSM messages:

- **Storage**: In-memory (single instance) or Redis (multi-instance)
- **TTL**: Configurable (default: 30 seconds)
- **Index**: Geohash-based index for fast lookups

**Interface**: `DeviceLocationCache`
- `updateLocation(deviceId, lat, lon, geohash)` - Update device location
- `getLocation(deviceId)` - Get device location
- `findDevicesInGeohashes(geohashes)` - Find devices in geohash areas
- `removeDevice(deviceId)` - Remove device from cache

### 2. Geo Router Service

**Responsibilities**:
1. **Ingress Handler**: Processes BSM messages from clients
   - Decodes BSM to extract device ID and coordinates
   - Caches device location
   
2. **Geohash-Filtered Handler**: Processes messages from TMC apps
   - Decodes message to extract coordinates
   - Calculates relevant geohashes
   - Finds devices in those geohashes
   - Routes messages directly to relevant clients

### 3. MQTT Client Service

Manages MQTT connections and subscriptions:
- Subscribes to ingress topic (`v2x/bsm/publish`)
- Subscribes to geohash-filtered topics (`v2x/georelevance/+/+/+/+/+/+/+/BSM`)
- Publishes to client egress topics (`v2x/client/{deviceId}/messages`)

## Configuration

### MQTT Configuration

```yaml
mqtt:
  broker-url: tcp://localhost:1883
  subscribe-topic: v2x/bsm/publish                    # Ingress
  geohash-filtered-topic-pattern: v2x/georelevance/+/+/+/+/+/+/+/BSM  # TMC apps
  client-egress-topic-pattern: v2x/client/{deviceId}/messages  # Egress
```

### Device Location Cache Configuration

```yaml
geo-routing:
  cache:
    enabled: true
    ttl-seconds: 30              # Location TTL
    cleanup-interval-seconds: 60   # Cleanup interval
```

## Benefits

1. **Simplified Client Interface**: Clients only need to know two topics:
   - Ingress: `v2x/bsm/publish`
   - Egress: `v2x/client/{deviceId}/messages`

2. **Efficient Routing**: Messages are only sent to relevant clients based on actual device locations

3. **Scalable**: Can use Redis for multi-instance deployments

4. **Flexible**: TMC apps can use geohash-filtered topics for distribution, while geo router handles client routing

5. **Mobile-Friendly**: Device locations are cached and updated as vehicles move

## Usage Example

### Client (Vehicle) Code

```python
# Publish BSM
client.publish("v2x/bsm/publish", bsm_bytes)

# Subscribe to relevant messages
client.subscribe("v2x/client/{my_device_id}/messages")
```

### TMC Application Code

```python
# Publish filtered message to geohash topic
geohash_path = "/9/q/8/y/y/k/7"
topic = f"v2x/georelevance{geohash_path}/BSM"
client.publish(topic, message_bytes)
```

The geo router will automatically route this message to all clients in the relevant geohash areas.

## Future Enhancements

- **Redis Support**: Add Redis-backed cache for multi-instance deployments
- **Distance-Based Filtering**: Filter messages by distance, not just geohash
- **Direction-Based Filtering**: Filter messages by vehicle heading
- **Message Prioritization**: Prioritize messages based on urgency or type




