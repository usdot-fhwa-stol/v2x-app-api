package usdot.v2x.georouter.geospatial;

import ch.hsr.geohash.GeoHash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import usdot.v2x.georouter.config.GeoRoutingConfig;
import usdot.v2x.georouter.models.GeoRelevanceMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for geohash-based geographic routing.
 * Calculates relevant geohash cells for a message location and determines
 * which subscribers should receive the message.
 */
@Slf4j
@Component
public class GeohashRouter {

    private final GeoRoutingConfig config;

    public GeohashRouter(GeoRoutingConfig config) {
        this.config = config;
    }

    /**
     * Calculates the primary geohash for a given latitude and longitude.
     * 
     * @param latitude Latitude in decimal degrees
     * @param longitude Longitude in decimal degrees
     * @return Geohash string at configured precision
     */
    public String calculateGeohash(double latitude, double longitude) {
        int precision = config.getGeohash().getPrecision();
        GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, precision);
        return geoHash.toBase32();
    }

    /**
     * Calculates all relevant geohashes for a message location.
     * Includes the primary geohash and neighboring geohashes based on radius.
     * 
     * @param message GeoRelevanceMessage with coordinates
     * @return Set of geohash strings representing relevant geographic areas
     */
    public Set<String> calculateRelevantGeohashes(GeoRelevanceMessage message) {
        if (message.getLatitude() == null || message.getLongitude() == null) {
            log.warn("Message missing coordinates, cannot calculate geohashes");
            return Set.of();
        }

        return calculateRelevantGeohashes(message.getLatitude(), message.getLongitude());
    }

    /**
     * Calculates all relevant geohashes for given coordinates.
     * 
     * @param latitude Latitude in decimal degrees
     * @param longitude Longitude in decimal degrees
     * @return Set of geohash strings representing relevant geographic areas
     */
    public Set<String> calculateRelevantGeohashes(double latitude, double longitude) {
        Set<String> geohashes = new HashSet<>();
        int precision = config.getGeohash().getPrecision();
        int radius = config.getGeohash().getNeighborRadius();

        // Calculate primary geohash
        GeoHash primaryHash = GeoHash.withCharacterPrecision(latitude, longitude, precision);
        geohashes.add(primaryHash.toBase32());

        // Add neighboring geohashes based on radius
        if (radius > 0) {
            List<GeoHash> neighbors = getNeighbors(primaryHash, radius);
            for (GeoHash neighbor : neighbors) {
                geohashes.add(neighbor.toBase32());
            }
        }

        log.debug("Calculated {} relevant geohashes for lat={}, lon={}", 
            geohashes.size(), latitude, longitude);

        return geohashes;
    }

    /**
     * Gets neighboring geohashes within a specified radius.
     * 
     * @param centerHash Center geohash
     * @param radius Radius in geohash cells (1 = immediate neighbors, 2 = neighbors + their neighbors, etc.)
     * @return List of neighboring geohashes
     */
    private List<GeoHash> getNeighbors(GeoHash centerHash, int radius) {
        List<GeoHash> neighbors = new ArrayList<>();
        
        if (radius <= 0) {
            return neighbors;
        }

        // Get immediate neighbors (8 directions)
        GeoHash[] immediateNeighbors = centerHash.getAdjacent();
        neighbors.addAll(List.of(immediateNeighbors));

        // For radius > 1, recursively get neighbors of neighbors
        if (radius > 1) {
            Set<String> seen = new HashSet<>();
            seen.add(centerHash.toBase32());
            for (GeoHash neighbor : immediateNeighbors) {
                seen.add(neighbor.toBase32());
            }

            List<GeoHash> currentLevel = List.of(immediateNeighbors);
            for (int i = 1; i < radius; i++) {
                List<GeoHash> nextLevel = new ArrayList<>();
                for (GeoHash hash : currentLevel) {
                    GeoHash[] adjacents = hash.getAdjacent();
                    for (GeoHash adj : adjacents) {
                        if (!seen.contains(adj.toBase32())) {
                            seen.add(adj.toBase32());
                            neighbors.add(adj);
                            nextLevel.add(adj);
                        }
                    }
                }
                currentLevel = nextLevel;
            }
        }

        return neighbors;
    }

    /**
     * Checks if a geohash matches any of the target geohashes (exact match or prefix match).
     * 
     * @param messageGeohash Geohash from the message
     * @param targetGeohashes Set of geohashes the subscriber is interested in
     * @return true if there's a match
     */
    public boolean matchesGeohash(String messageGeohash, Set<String> targetGeohashes) {
        // Exact match
        if (targetGeohashes.contains(messageGeohash)) {
            return true;
        }

        // Prefix match: check if message geohash starts with any target geohash
        // (subscriber subscribed to a larger area)
        for (String target : targetGeohashes) {
            if (messageGeohash.startsWith(target)) {
                return true;
            }
        }

        // Reverse prefix match: check if any target geohash starts with message geohash
        // (subscriber subscribed to a smaller area within the message area)
        for (String target : targetGeohashes) {
            if (target.startsWith(messageGeohash)) {
                return true;
            }
        }

        return false;
    }
}


