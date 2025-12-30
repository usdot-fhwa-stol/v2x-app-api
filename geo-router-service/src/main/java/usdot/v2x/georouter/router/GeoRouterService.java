package usdot.v2x.georouter.router;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.stereotype.Service;
import usdot.v2x.georouter.cache.DeviceLocationCache;
import usdot.v2x.georouter.config.MqttConfig;
import usdot.v2x.georouter.decoder.BsmDecoderService;
import usdot.v2x.georouter.geospatial.GeohashRouter;
import usdot.v2x.georouter.mqtt.MqttClientService;
import usdot.v2x.georouter.models.GeoRelevanceMessage;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Core geo-routing service that coordinates message decoding, geohash
 * calculation,
 * and message routing to subscribers.
 */
@Slf4j
@Service
public class GeoRouterService {

    private final MqttClientService mqttClientService;
    private final BsmDecoderService bsmDecoderService;
    private final GeohashRouter geohashRouter;
    private final DeviceLocationCache deviceLocationCache;
    private final GeoRouterConfig config;
    private final MqttConfig mqttConfig;

    public GeoRouterService(
            MqttClientService mqttClientService,
            BsmDecoderService bsmDecoderService,
            GeohashRouter geohashRouter,
            DeviceLocationCache deviceLocationCache,
            GeoRouterConfig config,
            MqttConfig mqttConfig) {
        this.mqttClientService = mqttClientService;
        this.bsmDecoderService = bsmDecoderService;
        this.geohashRouter = geohashRouter;
        this.deviceLocationCache = deviceLocationCache;
        this.config = config;
        this.mqttConfig = mqttConfig;
    }

    @PostConstruct
    public void initialize() {
        if (!config.isEnabled()) {
            log.info("Geo routing is disabled, skipping initialization");
            return;
        }

        try {
            // Subscribe to ingress topic (BSM messages from clients)
            mqttClientService.subscribeToIngress(this::handleIngressMessage);
            log.info("Subscribed to ingress topic");

            log.info("Geo Router Service initialized");
        } catch (MqttException e) {
            log.error("Failed to subscribe to topics", e);
            throw new RuntimeException("Failed to initialize geo router", e);
        }
    }

    /**
     * Handles incoming BSM messages from the ingress topic (v2x/bsm/publish).
     * Extracts MQTT client ID from user properties and routes based on registered locations.
     */
    private void handleIngressMessage(String topic, MqttMessage mqttMessage) {
        try {
            byte[] payload = mqttMessage.getPayload();

            log.debug("Processing ingress BSM message from topic: {}, size: {} bytes",
                    topic, payload.length);

            // Extract MQTT client ID from user properties (MQTT v5)
            String mqttClientId = extractMqttClientId(mqttMessage);
            if (mqttClientId == null || mqttClientId.isEmpty()) {
                log.warn("MQTT client ID not found in message user properties, skipping routing");
                return;
            }

            // Decode BSM and extract coordinates
            GeoRelevanceMessage geoMessage = bsmDecoderService.decodeBsm(payload, topic);

            if (geoMessage == null) {
                log.warn("Failed to decode BSM message, skipping");
                return;
            }

            log.debug("Successfully decoded BSM message. MQTT Client ID: {}, Coordinates: lat={}, lon={}",
                    mqttClientId, geoMessage.getLatitude(), geoMessage.getLongitude());

            // Update device location in database via REST API (or cache if using in-memory)
            // Note: Location updates should be done via REST API registration endpoint
            // This is just for routing purposes - we use the location from the BSM for routing

            // Route this BSM to relevant clients based on registered MQTT client IDs
            CompletableFuture.runAsync(() -> routeBsmToClients(mqttClientId, geoMessage))
                    .exceptionally(ex -> {
                        log.error("Error routing BSM to clients", ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Error handling ingress message", e);
        }
    }

    /**
     * Extracts MQTT client ID from MQTT v5 message user properties.
     * Falls back to BSM device ID if not found in user properties.
     */
    private String extractMqttClientId(MqttMessage mqttMessage) {
        try {
            // Try to get from user properties (MQTT v5)
            if (mqttMessage.getProperties() != null) {
                var userProperties = mqttMessage.getProperties().getUserProperties();
                if (userProperties != null) {
                    for (var prop : userProperties) {
                        if ("mqttClientId".equals(prop.getKey()) || "clientId".equals(prop.getKey())) {
                            String clientId = prop.getValue().toString();
                            log.debug("Extracted MQTT client ID from user properties: {}", clientId);
                            return clientId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract MQTT client ID from user properties: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Routes a BSM message to relevant clients based on registered MQTT client IDs.
     * Finds devices within the message's geohash area and publishes to their egress topics.
     * 
     * @param senderMqttClientId MQTT client ID of the message sender
     * @param message Geo-relevance message containing location and BSM data
     */
    public void routeBsmToClients(String senderMqttClientId, GeoRelevanceMessage message) {
        if (message.getLatitude() == null || message.getLongitude() == null) {
            log.warn("Message missing coordinates, cannot route");
            return;
        }

        // Calculate relevant geohashes for this message
        Set<String> relevantGeohashes = geohashRouter.calculateRelevantGeohashes(message);

        if (relevantGeohashes.isEmpty()) {
            log.warn("No relevant geohashes calculated for message");
            return;
        }

        // Find registered MQTT client IDs in the relevant geohashes
        Set<String> relevantMqttClientIds = deviceLocationCache.findDevicesInGeohashes(relevantGeohashes);

        if (relevantMqttClientIds.isEmpty()) {
            log.debug("No registered devices found in relevant geohashes: {}", relevantGeohashes);
            return;
        }

        // Filter out the sender's MQTT client ID (don't send message back to sender)
        Set<String> recipients = new java.util.HashSet<>(relevantMqttClientIds);
        if (senderMqttClientId != null) {
            recipients.remove(senderMqttClientId);
        }

        if (recipients.isEmpty()) {
            log.debug("No other devices found in relevant geohashes (only sender: {})", senderMqttClientId);
            return;
        }

        log.info("Routing BSM from MQTT client {} to {} other clients in geohashes: {}",
                senderMqttClientId, recipients.size(), relevantGeohashes);

        // Publish message to each relevant device's egress topic using their MQTT client ID
        int publishedCount = 0;
        String clientEgressTopicPattern = mqttConfig.getClientEgressTopicPattern();

        for (String mqttClientId : recipients) {
            try {
                // Build client egress topic: v2x/client/{mqttClientId}/messages
                String topic = clientEgressTopicPattern.replace("{deviceId}", mqttClientId);

                log.debug("Publishing to client topic: {}", topic);
                mqttClientService.publish(topic, message.getAsn1Binary());
                publishedCount++;
            } catch (MqttException e) {
                log.error("Failed to publish message to MQTT client: {}", mqttClientId, e);
            }
        }

        log.info("Successfully routed message to {}/{} clients",
                publishedCount, recipients.size());
    }

    /**
     * Converts a geohash string to hierarchical path format.
     * Example: "9q8yyk7" -> "/9/q/8/y/y/k/7"
     * 
     * @param geohash Geohash string
     * @return Hierarchical path representation
     */
    private String formatGeohashAsPath(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            return "";
        }

        StringBuilder path = new StringBuilder();
        for (char c : geohash.toCharArray()) {
            path.append('/').append(c);
        }
        return path.toString();
    }
}
