package usdot.v2x.hivemq.routing.service;

import ch.hsr.geohash.GeoHash;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.publish.PublishService;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for routing messages to geohash-based Regional topics
 * Implements 3x3 geohash expansion for regional coverage
 */
@Slf4j
public class GeohashRoutingService {

    private static final int GEOHASH_PRECISION = 7; // Level 7 geohash
    private static final int GRID_SIZE = 3; // 3x3 grid expansion

    /**
     * Route message to geohash-based Regional topics
     * Computes primary geohash and 3x3 neighbors, then publishes to each
     * 
     * @param location    Location data from message
     * @param messageType Message type (e.g., "BSM")
     * @param payload     Original message payload
     */
    public void routeToGeohashTopics(
            LocationData location,
            String messageType,
            ByteBuffer payload) {

        try {
            // Compute primary geohash
            GeoHash primaryGeohash = GeoHash.withCharacterPrecision(
                    location.getLatitude(),
                    location.getLongitude(),
                    GEOHASH_PRECISION);

            // Get 3x3 grid of geohashes
            List<GeoHash> geohashes = expandTo3x3Grid(primaryGeohash);

            log.debug("Routing {} message to {} geohash topics", messageType, geohashes.size());

            // Publish to each geohash topic
            PublishService publishService = Services.publishService();

            for (GeoHash geohash : geohashes) {
                String geohashTopic = buildGeohashTopic(
                        geohash,
                        messageType);

                // Create publish object
                com.hivemq.extension.sdk.api.services.publish.Publish publish = com.hivemq.extension.sdk.api.services.publish.Publish
                        .builder()
                        .topic(geohashTopic)
                        .payload(payload.duplicate())
                        .qos(com.hivemq.extension.sdk.api.packets.general.Qos.AT_LEAST_ONCE)
                        .retain(false)
                        .build();

                // Publish asynchronously
                publishService.publish(publish);

                log.debug("Published to geohash topic: {}", geohashTopic);
            }

        } catch (Exception e) {
            log.error("Error routing to geohash topics: {}", e.getMessage(), e);
        }
    }

    /**
     * Build geohash topic
     * Format:
     * v2x/1/geo/{char1}/{char2}/{char3}/{char4}/{char5}/{char6}/{char7}/{messageType}
     */
    private String buildGeohashTopic(
            GeoHash geohash,
            String messageType) {

        String geohashStr = geohash.toBase32();

        // For level 7 geohash, we use all 7 characters
        // Format:
        // v2x/1/geo/{char1}/{char2}/{char3}/{char4}/{char5}/{char6}/{char7}/{messageType}
        StringBuilder topic = new StringBuilder("v2x/1/geo/");

        // Add each geohash character as a separate segment
        for (int i = 0; i < geohashStr.length(); i++) {
            topic.append(geohashStr.charAt(i));
            topic.append("/");
        }

        topic.append(messageType.toLowerCase());

        return topic.toString();
    }

    /**
     * Expand geohash to 3x3 grid (primary + 8 neighbors)
     * Uses the geohash library's neighbor methods
     * 
     * @param centerGeohash Center geohash
     * @return List of 9 geohashes (center + 8 neighbors)
     */
    private List<GeoHash> expandTo3x3Grid(GeoHash centerGeohash) {
        List<GeoHash> geohashes = new ArrayList<>();

        // Add center geohash
        geohashes.add(centerGeohash);

        // Get neighbors in all 8 directions
        try {
            // North
            GeoHash north = centerGeohash.getNorthernNeighbour();
            if (north != null)
                geohashes.add(north);

            // Northeast
            GeoHash northeast = north != null ? north.getEasternNeighbour() : null;
            if (northeast != null)
                geohashes.add(northeast);

            // East
            GeoHash east = centerGeohash.getEasternNeighbour();
            if (east != null)
                geohashes.add(east);

            // Southeast
            GeoHash southeast = east != null ? east.getSouthernNeighbour() : null;
            if (southeast != null)
                geohashes.add(southeast);

            // South
            GeoHash south = centerGeohash.getSouthernNeighbour();
            if (south != null)
                geohashes.add(south);

            // Southwest
            GeoHash southwest = south != null ? south.getWesternNeighbour() : null;
            if (southwest != null)
                geohashes.add(southwest);

            // West
            GeoHash west = centerGeohash.getWesternNeighbour();
            if (west != null)
                geohashes.add(west);

            // Northwest
            GeoHash northwest = west != null ? west.getNorthernNeighbour() : null;
            if (northwest != null)
                geohashes.add(northwest);

        } catch (Exception e) {
            log.warn("Error getting geohash neighbors, using center only: {}", e.getMessage());
        }

        // Remove duplicates and ensure we have at least the center
        return geohashes.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculate latitude step size for geohash precision
     */
    private double calculateLatStep(int precision) {
        // Approximate step size for level 7 geohash: ~0.153 km
        // This is a simplified calculation
        int latBits = (precision * 5) / 2;
        return 180.0 / Math.pow(2, latBits);
    }

    /**
     * Calculate longitude step size for geohash precision
     */
    private double calculateLonStep(int precision) {
        // Approximate step size for level 7 geohash: ~0.153 km
        int lonBits = (precision * 5 + 1) / 2;
        return 360.0 / Math.pow(2, lonBits);
    }

    public void cleanup() {
        // Cleanup resources if needed
    }
}
