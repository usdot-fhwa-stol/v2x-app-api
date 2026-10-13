package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.etx.configuration.geometry.Polygon;
import usdot.v2x.app.api.services.ErrorLoggingService;

class OverrideGeofenceResolverTest {

    /** ~2.2 km × 2.2 km square: several precision-7 representatives, under maxGeohashes. */
    private static final double[][] LARGE_OVERRIDE_RING = {
            {-83.27, 42.26},
            {-83.27, 42.28},
            {-83.25, 42.28},
            {-83.25, 42.26},
            {-83.27, 42.26}
    };

    /** ~20 m × 20 m square that stays at the minimum geohash count. */
    private static final double[][] TINY_GENERATED_RING = {
            {-83.0470, 42.3280},
            {-83.0470, 42.3282},
            {-83.0468, 42.3282},
            {-83.0468, 42.3280},
            {-83.0470, 42.3280}
    };

    private GeofenceProperties properties;
    private GeohashUtils geohashUtils;
    private OverrideGeofenceResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new GeofenceProperties();
        properties.getGeohash().setPrecision(7);
        properties.getLimits().setMinGeohashes(1);
        properties.getLimits().setMaxGeohashes(500);
        properties.getLimits().setMaxCandidateCells(100_000);
        properties.getLimits().setAllowOverlappingGeohashes(true);

        geohashUtils = new GeohashUtils(
                new ObjectMapper(),
                mock(ErrorLoggingService.class),
                properties,
                mock(JdbcTemplate.class));
        resolver = new OverrideGeofenceResolver(geohashUtils, properties);
    }

    @Test
    void largeOverrideExceedsLowThreshold() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        int count = geohashCount(override);
        assertTrue(count > 1, "Large override should produce multiple geohashes, got " + count);

        properties.getLimits().setOverrideGeohashThreshold(count - 1);

        assertTrue(resolver.exceedsThreshold(override),
                "Override covering " + count + " geohashes should exceed threshold " + (count - 1));
    }

    @Test
    void largeOverrideIsKeptWhenAtThreshold() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        int count = geohashCount(override);
        properties.getLimits().setOverrideGeohashThreshold(count);

        assertFalse(resolver.exceedsThreshold(override),
                "Override covering " + count + " geohashes should not exceed threshold " + count);
    }

    @Test
    void tinyOverrideIsKeptUnderDefaultThreshold() {
        GeofenceFeatureCollection override = polygon(TINY_GENERATED_RING);
        int count = geohashCount(override);
        properties.getLimits().setOverrideGeohashThreshold(50);

        assertTrue(count <= 50, "Tiny override should stay under default threshold, got " + count);
        assertFalse(resolver.exceedsThreshold(override));
    }

    @Test
    void zeroThresholdAlwaysGeneratesFromMessage() {
        GeofenceFeatureCollection override = polygon(TINY_GENERATED_RING);
        properties.getLimits().setOverrideGeohashThreshold(0);

        assertTrue(resolver.exceedsThreshold(override));
    }

    @Test
    void geofenceTooLargeIsTreatedAsExceedingThreshold() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        int count = geohashCount(override);
        assertTrue(count > 1, "Need more than one geohash so maxGeohashes=1 can reject");
        properties.getLimits().setMaxGeohashes(1);
        properties.getLimits().setOverrideGeohashThreshold(500);

        assertTrue(resolver.exceedsThreshold(override));
    }

    @Test
    void resolveUsesGeneratedGeofenceWhenOverrideExceedsThreshold() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        GeofenceFeatureCollection generated = polygon(TINY_GENERATED_RING);
        int count = geohashCount(override);
        properties.getLimits().setOverrideGeohashThreshold(Math.max(1, count - 1));

        AtomicBoolean generatedCalled = new AtomicBoolean(false);
        GeofenceFeatureCollection resolved = resolver.resolve(override, () -> {
            generatedCalled.set(true);
            return generated;
        });

        assertTrue(generatedCalled.get(), "Message geofence should be generated when override exceeds threshold");
        assertSame(generated, resolved);
    }

    @Test
    void resolveKeepsOverrideWhenUnderThreshold() {
        GeofenceFeatureCollection override = polygon(TINY_GENERATED_RING);
        GeofenceFeatureCollection generated = polygon(LARGE_OVERRIDE_RING);
        properties.getLimits().setOverrideGeohashThreshold(50);

        AtomicBoolean generatedCalled = new AtomicBoolean(false);
        GeofenceFeatureCollection resolved = resolver.resolve(override, () -> {
            generatedCalled.set(true);
            return generated;
        });

        assertFalse(generatedCalled.get(), "Message geofence should not be generated for a small override");
        assertSame(override, resolved);
    }

    @Test
    void resolveGeneratesWhenOverrideIsMissing() {
        GeofenceFeatureCollection generated = polygon(TINY_GENERATED_RING);

        GeofenceFeatureCollection resolved = resolver.resolve(null, () -> generated);

        assertSame(generated, resolved);
    }

    private int geohashCount(GeofenceFeatureCollection geofence) {
        return geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                geofence, properties.getGeohash().getPrecision(), Set.of()).size();
    }

    private static GeofenceFeatureCollection polygon(double[][] ring) {
        List<List<Double>> coordinates = new ArrayList<>();
        for (double[] point : ring) {
            coordinates.add(List.of(point[0], point[1]));
        }

        Polygon geometry = new Polygon();
        geometry.setCoordinates(List.of(coordinates));

        GeofenceFeature feature = new GeofenceFeature();
        feature.setType("Feature");
        feature.setGeometry(geometry);
        feature.setProperties(Map.of());

        GeofenceFeatureCollection collection = new GeofenceFeatureCollection();
        collection.setType("FeatureCollection");
        collection.setFeatures(List.of(feature));
        return collection;
    }
}
