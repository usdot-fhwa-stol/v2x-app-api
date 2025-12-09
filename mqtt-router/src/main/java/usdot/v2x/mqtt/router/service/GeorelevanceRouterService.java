package usdot.v2x.mqtt.router.service;

import ch.hsr.geohash.GeoHash;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.stereotype.Service;
import usdot.v2x.mqtt.router.config.RouterProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that routes messages from GeoRelevance to Regional topics.
 * 
 * Flow:
 * 1. Clients publish to:
 * v2x/1/GeoRelevance/${clientType}/${clientSubtype}/Public/{messageType}
 * 2. Router subscribes to GeoRelevance topics and forwards eligible messages to
 * Regional topics
 * 3. Router tracks client subscriptions for management purposes
 * 
 * Note: Clients subscribe directly to GeoRelevance topics via MQTT to receive
 * messages.
 * The router does NOT republish to GeoRelevance topics to avoid infinite loops.
 */

@Slf4j
@Service
public class GeorelevanceRouterService {
    private final MqttClientService mqttClientService;
    private final RouterProperties routerProperties;
    private final ObjectMapper objectMapper;

    // Track active client subscriptions to georelevance topics
    // Key: clientId, Value: Set of subscribed topic patterns
    private final Map<String, Set<String>> clientSubscriptions = new ConcurrentHashMap<>();

    // Pattern for georelevance topics:
    // v2x/1/GeoRelevance/${clientType}/${clientSubtype}/Public/{messageType}
    private static final Pattern GEORELEVANCE_PATTERN = Pattern.compile(
            "v2x/1/GeoRelevance/([^/]+)/([^/]+)/Public/(BSM|PSM|SPAT|MAP|RSA|TIM|SDSM|TUM)");

    // Allowed message types for regional routing
    private static final Set<String> REGIONAL_MESSAGE_TYPES = Set.of(
            "RSA", "TIM", "SPAT", "MAP", "BSM", "SDSM");

    public GeorelevanceRouterService(
            MqttClientService mqttClientService,
            RouterProperties routerProperties,
            ObjectMapper objectMapper) {
        this.mqttClientService = mqttClientService;
        this.routerProperties = routerProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (!routerProperties.isEnabled()) {
            log.info("Georelevance router is disabled");
            return;
        }

        // Subscribe to all georelevance topics
        String georelevanceTopic = "v2x/1/GeoRelevance/+/+/Public/+";
        mqttClientService.subscribe(georelevanceTopic, this::handleGeorelevanceMessage);

        log.info("Georelevance router service initialized, subscribed to: {}", georelevanceTopic);
    }

    /**
     * Handle messages published to georelevance topics
     */
    private void handleGeorelevanceMessage(String topic, MqttMessage message) {
        try {
            log.debug("Received message on georelevance topic: {}", topic);

            // Parse the georelevance topic
            Matcher matcher = GEORELEVANCE_PATTERN.matcher(topic);
            if (!matcher.matches()) {
                log.warn("Received message on invalid georelevance topic format: {}", topic);
                return;
            }

            String clientType = matcher.group(1);
            String clientSubtype = matcher.group(2);
            String messageType = matcher.group(3);

            // Route to regional topic if message type is allowed
            if (REGIONAL_MESSAGE_TYPES.contains(messageType)) {
                String regionalTopic = buildRegionalTopic(clientType, clientSubtype, messageType, message.getPayload());
                if (regionalTopic != null) {
                    mqttClientService.publish(regionalTopic, message.getPayload());
                    log.info("Routed message from {} to regional topic: {}", topic, regionalTopic);
                } else {
                    log.warn("Could not build regional topic for message on {}: location not found in payload. " +
                            "Message must be a valid JSON-encoded V2X message with location data.", topic);
                }
            }

            // Note: We do NOT republish to GeoRelevance topics here to avoid infinite
            // loops.
            // Clients that want to receive messages should subscribe directly to
            // GeoRelevance topics via MQTT.
            // The subscription tracking via REST API is for management/monitoring purposes
            // only.

        } catch (Exception e) {
            log.error("Error handling georelevance message on topic {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Build regional topic with geohash:
     * v2x/1/Regional/{char1}/{char2}/{char3}/{char4}/{char5}/{char6}/{char7}/{char8}/${clientType}/${clientSubtype}/Public/{messageType}
     * 
     * @param clientType    Client type from topic
     * @param clientSubtype Client subtype from topic
     * @param messageType   Message type from topic
     * @param payload       Message payload (JSON or ASN.1)
     * @return Regional topic with geohash, or null if location cannot be extracted
     */
    private String buildRegionalTopic(String clientType, String clientSubtype, String messageType, byte[] payload) {
        try {
            // Extract location from message payload
            Location location = extractLocation(messageType, payload);
            if (location == null) {
                return null;
            }

            // Calculate 8-character geohash
            GeoHash geoHash = GeoHash.withCharacterPrecision(location.latitude, location.longitude, 8);
            String geohashStr = geoHash.toBase32();

            // Build regional topic with each geohash character as a separate segment
            return String.format("v2x/1/Regional/%s/%s/%s/%s/%s/%s/%s/%s/%s/%s/Public/%s",
                    geohashStr.charAt(0),
                    geohashStr.charAt(1),
                    geohashStr.charAt(2),
                    geohashStr.charAt(3),
                    geohashStr.charAt(4),
                    geohashStr.charAt(5),
                    geohashStr.charAt(6),
                    geohashStr.charAt(7),
                    clientType,
                    clientSubtype,
                    messageType);
        } catch (Exception e) {
            log.error("Error building regional topic: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract latitude and longitude from message payload based on message type
     */
    private Location extractLocation(String messageType, byte[] payload) {
        if (payload == null || payload.length == 0) {
            log.warn("Empty payload, cannot extract location");
            return null;
        }

        try {
            // Try to parse as JSON first
            String payloadStr = new String(payload);

            // Quick check: if it's a plain text message (not JSON), skip
            if (!payloadStr.trim().startsWith("{") && !payloadStr.trim().startsWith("[")) {
                log.debug("Payload appears to be plain text, not JSON. Cannot extract location. Payload preview: {}",
                        payloadStr.length() > 100 ? payloadStr.substring(0, 100) + "..." : payloadStr);
                return null;
            }

            JsonNode root = objectMapper.readTree(payloadStr);

            // Navigate to message content - handle different wrapper formats
            JsonNode message = root;

            // Check if wrapped in MessageFrame: {"messageId": 20, "value":
            // {"BasicSafetyMessage": {...}}}
            if (root.has("value")) {
                JsonNode value = root.get("value");
                // Check for message type wrapper (e.g., "BasicSafetyMessage",
                // "PersonalSafetyMessage")
                String messageTypeKey = getMessageTypeKey(messageType);
                if (value.has(messageTypeKey)) {
                    message = value.get(messageTypeKey);
                } else {
                    // If no message type wrapper, use value directly
                    message = value;
                }
            }

            // Check if wrapped in "message" field
            if (message.has("message")) {
                message = message.get("message");
            }

            Location location = null;
            switch (messageType) {
                case "BSM":
                    location = extractBSMLocation(message);
                    break;
                case "PSM":
                    location = extractPSMLocation(message);
                    break;
                case "SDSM":
                    location = extractSDSMLocation(message);
                    break;
                case "SPAT":
                    location = extractSPATLocation(message);
                    break;
                case "MAP":
                    location = extractMAPLocation(message);
                    break;
                case "RSA":
                    location = extractRSALocation(message);
                    break;
                case "TIM":
                    location = extractTIMLocation(message);
                    break;
                default:
                    log.warn("Unknown message type for location extraction: {}", messageType);
                    return null;
            }

            if (location == null) {
                log.debug(
                        "Could not extract location from {} message. Message structure may not match expected format.",
                        messageType);
            } else {
                log.debug("Extracted location from {} message: lat={}, lon={}", messageType, location.latitude,
                        location.longitude);
            }

            return location;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.debug("Payload is not valid JSON (may be ASN.1 binary or plain text): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Error extracting location from {} message: {}", messageType, e.getMessage());
            return null;
        }
    }

    /**
     * Get the JSON key name for a message type (e.g., "BSM" ->
     * "BasicSafetyMessage")
     */
    private String getMessageTypeKey(String messageType) {
        return switch (messageType) {
            case "BSM" -> "BasicSafetyMessage";
            case "PSM" -> "PersonalSafetyMessage";
            case "SDSM" -> "SensorDataSharingMessage";
            case "SPAT" -> "SignalPhaseAndTiming";
            case "MAP" -> "MapData";
            case "RSA" -> "RoadSideAlert";
            case "TIM" -> "TravelerInformation";
            default -> messageType;
        };
    }

    /**
     * Extract location from BSM (Basic Safety Message)
     * Path: coreData.lat, coreData.long
     */
    private Location extractBSMLocation(JsonNode message) {
        JsonNode coreData = message.path("coreData");
        if (coreData.isMissingNode()) {
            return null;
        }
        JsonNode lat = coreData.path("lat");
        JsonNode lon = coreData.path("long");
        if (lat.isMissingNode() || lon.isMissingNode()) {
            return null;
        }
        // BSM uses microdegrees (multiply by 1e-6)
        double latitude = lat.asDouble() * 1e-6;
        double longitude = lon.asDouble() * 1e-6;
        return new Location(latitude, longitude);
    }

    /**
     * Extract location from PSM (Personal Safety Message)
     * Path: position.lat, position.long
     */
    private Location extractPSMLocation(JsonNode message) {
        JsonNode position = message.path("position");
        if (position.isMissingNode()) {
            return null;
        }
        JsonNode lat = position.path("lat");
        JsonNode lon = position.path("long");
        if (lat.isMissingNode() || lon.isMissingNode()) {
            return null;
        }
        // PSM uses microdegrees (multiply by 1e-6)
        double latitude = lat.asDouble() * 1e-6;
        double longitude = lon.asDouble() * 1e-6;
        return new Location(latitude, longitude);
    }

    /**
     * Extract location from SDSM (Sensor Data Sharing Message)
     * Path: refPos.lat, refPos.long
     */
    private Location extractSDSMLocation(JsonNode message) {
        JsonNode refPos = message.path("refPos");
        if (refPos.isMissingNode()) {
            return null;
        }
        JsonNode lat = refPos.path("lat");
        JsonNode lon = refPos.path("long");
        if (lat.isMissingNode() || lon.isMissingNode()) {
            return null;
        }
        // SDSM uses microdegrees (multiply by 1e-6)
        double latitude = lat.asDouble() * 1e-6;
        double longitude = lon.asDouble() * 1e-6;
        return new Location(latitude, longitude);
    }

    /**
     * Extract location from SPAT (Signal Phase and Timing)
     * SPAT messages contain intersections, extract from first intersection
     */
    private Location extractSPATLocation(JsonNode message) {
        JsonNode intersections = message.path("intersections");
        if (intersections.isMissingNode() || !intersections.isArray() || intersections.size() == 0) {
            return null;
        }
        JsonNode firstIntersection = intersections.get(0);
        JsonNode refPoint = firstIntersection.path("refPoint");
        if (refPoint.isMissingNode()) {
            return null;
        }
        JsonNode lat = refPoint.path("lat");
        JsonNode lon = refPoint.path("long");
        if (lat.isMissingNode() || lon.isMissingNode()) {
            return null;
        }
        double latitude = lat.asDouble() * 1e-6;
        double longitude = lon.asDouble() * 1e-6;
        return new Location(latitude, longitude);
    }

    /**
     * Extract location from MAP (Map Data)
     * MAP messages contain intersections, extract from first intersection
     */
    private Location extractMAPLocation(JsonNode message) {
        JsonNode intersections = message.path("intersections");
        if (intersections.isMissingNode() || !intersections.isArray() || intersections.size() == 0) {
            return null;
        }
        JsonNode firstIntersection = intersections.get(0);
        JsonNode refPoint = firstIntersection.path("refPoint");
        if (refPoint.isMissingNode()) {
            return null;
        }
        JsonNode lat = refPoint.path("lat");
        JsonNode lon = refPoint.path("long");
        if (lat.isMissingNode() || lon.isMissingNode()) {
            return null;
        }
        double latitude = lat.asDouble() * 1e-6;
        double longitude = lon.asDouble() * 1e-6;
        return new Location(latitude, longitude);
    }

    /**
     * Extract location from RSA (Road Side Alert)
     * Path: description.geometry.path (first point) or
     * description.geometry.radius.center
     */
    private Location extractRSALocation(JsonNode message) {
        JsonNode description = message.path("description");
        if (description.isMissingNode()) {
            return null;
        }

        // Try radius center first
        JsonNode geometry = description.path("geometry");
        JsonNode radius = geometry.path("radius");
        if (!radius.isMissingNode()) {
            JsonNode center = radius.path("center");
            if (!center.isMissingNode()) {
                JsonNode lat = center.path("lat");
                JsonNode lon = center.path("long");
                if (!lat.isMissingNode() && !lon.isMissingNode()) {
                    double latitude = lat.asDouble() * 1e-6;
                    double longitude = lon.asDouble() * 1e-6;
                    return new Location(latitude, longitude);
                }
            }
        }

        // Try path (first point)
        JsonNode path = geometry.path("path");
        if (!path.isMissingNode() && path.isArray() && path.size() > 0) {
            JsonNode firstPoint = path.get(0);
            JsonNode lat = firstPoint.path("lat");
            JsonNode lon = firstPoint.path("long");
            if (!lat.isMissingNode() && !lon.isMissingNode()) {
                double latitude = lat.asDouble() * 1e-6;
                double longitude = lon.asDouble() * 1e-6;
                return new Location(latitude, longitude);
            }
        }

        return null;
    }

    /**
     * Extract location from TIM (Traveler Information Message)
     * Path: dataframes[0].geometry.points[0] or
     * dataframes[0].geometry.radius.center
     */
    private Location extractTIMLocation(JsonNode message) {
        JsonNode dataframes = message.path("dataframes");
        if (dataframes.isMissingNode() || !dataframes.isArray() || dataframes.size() == 0) {
            return null;
        }
        JsonNode firstDataframe = dataframes.get(0);
        JsonNode geometry = firstDataframe.path("geometry");

        // Try radius center first
        JsonNode radius = geometry.path("radius");
        if (!radius.isMissingNode()) {
            JsonNode center = radius.path("center");
            if (!center.isMissingNode()) {
                JsonNode lat = center.path("lat");
                JsonNode lon = center.path("long");
                if (!lat.isMissingNode() && !lon.isMissingNode()) {
                    double latitude = lat.asDouble() * 1e-6;
                    double longitude = lon.asDouble() * 1e-6;
                    return new Location(latitude, longitude);
                }
            }
        }

        // Try points (first point)
        JsonNode points = geometry.path("points");
        if (!points.isMissingNode() && points.isArray() && points.size() > 0) {
            JsonNode firstPoint = points.get(0);
            JsonNode lat = firstPoint.path("lat");
            JsonNode lon = firstPoint.path("long");
            if (!lat.isMissingNode() && !lon.isMissingNode()) {
                double latitude = lat.asDouble() * 1e-6;
                double longitude = lon.asDouble() * 1e-6;
                return new Location(latitude, longitude);
            }
        }

        return null;
    }

    /**
     * Simple location data class
     */
    private static class Location {
        final double latitude;
        final double longitude;

        Location(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * Register a client subscription to georelevance topics.
     * Note: This is for tracking/monitoring purposes. Clients should subscribe
     * directly via MQTT.
     */
    public void registerClientSubscription(String clientId, String topicPattern) {
        clientSubscriptions.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet())
                .add(topicPattern);
        log.info("Registered subscription tracking for client {} to pattern: {}", clientId, topicPattern);
        log.debug("Note: Client should subscribe directly to MQTT topic: {}", topicPattern);
    }

    /**
     * Unregister a client subscription
     */
    public void unregisterClientSubscription(String clientId, String topicPattern) {
        Set<String> subscriptions = clientSubscriptions.get(clientId);
        if (subscriptions != null) {
            subscriptions.remove(topicPattern);
            if (subscriptions.isEmpty()) {
                clientSubscriptions.remove(clientId);
            }
            log.info("Unregistered subscription tracking for client {} from pattern: {}", clientId, topicPattern);
        }
    }

    /**
     * Unregister all subscriptions for a client
     */
    public void unregisterClient(String clientId) {
        clientSubscriptions.remove(clientId);
        log.info("Unregistered all subscription tracking for client: {}", clientId);
    }

    /**
     * Get active subscriptions for a client (tracking only)
     */
    public Set<String> getClientSubscriptions(String clientId) {
        return new HashSet<>(clientSubscriptions.getOrDefault(clientId, Collections.emptySet()));
    }

    @PreDestroy
    public void cleanup() {
        clientSubscriptions.clear();
        log.info("Cleaned up client subscriptions");
    }
}
