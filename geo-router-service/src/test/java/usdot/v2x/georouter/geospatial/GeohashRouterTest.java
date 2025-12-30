package usdot.v2x.georouter.geospatial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usdot.v2x.georouter.config.GeoRoutingConfig;
import usdot.v2x.georouter.models.GeoRelevanceMessage;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GeohashRouter.
 */
class GeohashRouterTest {

    private GeohashRouter geohashRouter;
    private GeoRoutingConfig config;

    @BeforeEach
    void setUp() {
        config = new GeoRoutingConfig();
        GeoRoutingConfig.Geohash geohashConfig = new GeoRoutingConfig.Geohash();
        geohashConfig.setPrecision(7);
        geohashConfig.setNeighborRadius(1);
        config.setGeohash(geohashConfig);

        geohashRouter = new GeohashRouter(config);
    }

    @Test
    void testCalculateGeohash() {
        // Test with known coordinates (San Francisco area)
        double lat = 37.7749;
        double lon = -122.4194;

        String geohash = geohashRouter.calculateGeohash(lat, lon);
        assertNotNull(geohash);
        assertEquals(7, geohash.length()); // Precision 7
    }

    @Test
    void testCalculateRelevantGeohashes() {
        double lat = 37.7749;
        double lon = -122.4194;

        Set<String> geohashes = geohashRouter.calculateRelevantGeohashes(lat, lon);
        assertNotNull(geohashes);
        assertFalse(geohashes.isEmpty());
        // Should include primary geohash plus neighbors
        assertTrue(geohashes.size() >= 1);
    }

    @Test
    void testCalculateRelevantGeohashesFromMessage() {
        GeoRelevanceMessage message = GeoRelevanceMessage.builder()
                .timestamp(Instant.now())
                .latitude(37.7749)
                .longitude(-122.4194)
                .messageType("BSM")
                .build();

        Set<String> geohashes = geohashRouter.calculateRelevantGeohashes(message);
        assertNotNull(geohashes);
        assertFalse(geohashes.isEmpty());
    }

    @Test
    void testCalculateRelevantGeohashesWithNullCoordinates() {
        GeoRelevanceMessage message = GeoRelevanceMessage.builder()
                .timestamp(Instant.now())
                .latitude(null)
                .longitude(null)
                .messageType("BSM")
                .build();

        Set<String> geohashes = geohashRouter.calculateRelevantGeohashes(message);
        assertNotNull(geohashes);
        assertTrue(geohashes.isEmpty());
    }

    @Test
    void testMatchesGeohash() {
        Set<String> targetGeohashes = Set.of("9q8yyk7", "9q8yyk8");

        // Exact match
        assertTrue(geohashRouter.matchesGeohash("9q8yyk7", targetGeohashes));

        // Prefix match (message geohash starts with target)
        assertTrue(geohashRouter.matchesGeohash("9q8yyk7a", targetGeohashes));

        // Reverse prefix match (target starts with message geohash)
        assertTrue(geohashRouter.matchesGeohash("9q8yyk", targetGeohashes));

        // No match
        assertFalse(geohashRouter.matchesGeohash("9q8yyk9", targetGeohashes));
    }
}
