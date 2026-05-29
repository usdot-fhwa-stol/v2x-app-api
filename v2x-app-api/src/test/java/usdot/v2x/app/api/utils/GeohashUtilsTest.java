package usdot.v2x.app.api.utils;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.exceptions.NoAvailableGeohashException;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.etx.configuration.geometry.Polygon;
import usdot.v2x.app.api.services.ErrorLoggingService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GeohashUtilsTest {

    private static final int GRID_PRECISION = 7;
    private static final int MIN_CELL_SEPARATION = 3;

    /** Long curved road corridor (~3.5 km). */
    private static final double[][] CORRIDOR_RING = {
            {-83.2697110308155, 42.26604289753058},
            {-83.26970982883948, 42.26604385612132},
            {-83.267524029349, 42.266108335002926},
            {-83.26550073047956, 42.26620636557595},
            {-83.26397396702501, 42.26631465137696},
            {-83.26091148744074, 42.26657087185772},
            {-83.25908092290312, 42.26668768559875},
            {-83.25674635107237, 42.266786704127384},
            {-83.253018290311, 42.26689370731415},
            {-83.25025248417423, 42.26696289375523},
            {-83.2488594257548, 42.266997709373015},
            {-83.24876570513597, 42.26492831940178},
            {-83.25291414234628, 42.264824594031815},
            {-83.256625778986, 42.26471806527781},
            {-83.2589017935739, 42.264621359268176},
            {-83.26061779475647, 42.26451179879717},
            {-83.26367366934491, 42.26425613294218},
            {-83.26529839504232, 42.264141220596464},
            {-83.26739570962866, 42.26403994169808},
            {-83.26945591582405, 42.263979170746204},
            {-83.26945828571894, 42.26397729168379},
            {-83.27662683949674, 42.26378625888074},
            {-83.27766133286882, 42.26371874930958},
            {-83.27893835952752, 42.26359630956108},
            {-83.28044794985709, 42.263374091549075},
            {-83.28167960724129, 42.26313915289615},
            {-83.28286499929641, 42.26286736760552},
            {-83.28394567822583, 42.262569260020655},
            {-83.2851985434059, 42.26217178648538},
            {-83.28623716816355, 42.26179477186449},
            {-83.28770095315492, 42.261182740669184},
            {-83.28891545420052, 42.26067491606639},
            {-83.29028279020775, 42.26247921510587},
            {-83.28756915293158, 42.263613863833086},
            {-83.28749745466081, 42.26364183592694},
            {-83.28639054278413, 42.264043642077944},
            {-83.28632605504978, 42.264065558872545},
            {-83.28500973939552, 42.264483165489054},
            {-83.2837987255731, 42.26481850366864},
            {-83.28372459729309, 42.264837209569286},
            {-83.28240539398527, 42.26513842809805},
            {-83.28110367504596, 42.26538673246183},
            {-83.28102796685414, 42.26539951509262},
            {-83.27943424258977, 42.26563411941616},
            {-83.27934174575543, 42.26564534858891},
            {-83.2779900243527, 42.26577495119288},
            {-83.27677113107544, 42.26585426547342},
            {-83.2750183312512, 42.26590867864903},
            {-83.27255133200902, 42.2659756448534},
            {-83.2697110308155, 42.26604289753058}
    };

    /** Small intersection rectangle (~300 m × 300 m). */
    private static final double[][] SMALL_INTERSECTION_RING = {
            {-83.04713754413898, 42.32818024508649},
            {-83.04713759685912, 42.330921520597414},
            {-83.04358678933497, 42.330921521689845},
            {-83.04358684205512, 42.32818024399414},
            {-83.04713754413898, 42.32818024508649}
    };

    private GeofenceProperties geofenceProperties;
    private GeohashUtils geohashUtils;

    @BeforeEach
    void setUp() {
        geofenceProperties = new GeofenceProperties();
        geofenceProperties.getLimits().setMinGeohashes(4);
        geofenceProperties.getLimits().setAllowOverlappingGeohashes(false);

        geohashUtils = new GeohashUtils(
                new ObjectMapper(),
                mock(ErrorLoggingService.class),
                geofenceProperties,
                mock(JdbcTemplate.class));
    }

    @Test
    void corridorSelectsMultipleRepresentatives() {
        List<String> geohashes = extract(CORRIDOR_RING);

        assertTrue(geohashes.size() > 3,
                "Long corridor should produce multiple representatives, got: " + geohashes);
        assertTrue(geohashes.size() < 25,
                "Long corridor should stay sparse (9×9 tiling), got: " + geohashes);
        assertEquals(geohashes.size(), new HashSet<>(geohashes).size(), "Representatives must be unique");
    }

    @Test
    void corridorRepresentativesAreSpacedApart() {
        List<String> geohashes = extract(CORRIDOR_RING);

        assertTrue(minChebyshevSeparation(geohashes) >= MIN_CELL_SEPARATION,
                "Representatives should not be adjacent: " + geohashes);
    }

    @Test
    void corridorDoesNotCollapseToSingleCentroidGeohash() {
        List<String> geohashes = extract(CORRIDOR_RING);

        assertTrue(geohashes.size() > 1,
                "Regression: invalid BoundingBox order previously collapsed corridor to one geohash");
    }

    @Test
    void smallIntersectionMeetsMinimumGeohashes() {
        List<String> geohashes = extract(SMALL_INTERSECTION_RING);

        assertTrue(geohashes.size() >= geofenceProperties.getLimits().getMinGeohashes(),
                "Small region should reach configured minimum, got: " + geohashes);
        assertTrue(geohashes.size() <= 8,
                "Small region should not over-select, got: " + geohashes);
        assertTrue(minChebyshevSeparation(geohashes) >= MIN_CELL_SEPARATION,
                "Small region representatives should still be spaced: " + geohashes);
    }

    @Test
    void allowOverlappingGeohashesPermitsExistingUsedCell() {
        List<String> baseline = extract(CORRIDOR_RING);
        assertFalse(baseline.isEmpty());

        geofenceProperties.getLimits().setAllowOverlappingGeohashes(true);
        String shared = baseline.get(0);

        List<String> withSharing = geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                polygonFeatureCollection(CORRIDOR_RING),
                GRID_PRECISION,
                Set.of(shared));

        assertTrue(withSharing.contains(shared),
                "When sharing is allowed, an existing-used cell should remain selectable");
    }

    @Test
    void withoutAllowSharingAvoidsExistingUsedCells() {
        List<String> baseline = extract(CORRIDOR_RING);
        assertFalse(baseline.isEmpty());

        List<String> blocked = geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                polygonFeatureCollection(CORRIDOR_RING),
                GRID_PRECISION,
                new HashSet<>(baseline));

        for (String geohash : blocked) {
            assertFalse(baseline.contains(geohash),
                    "Without sharing, results should not reuse already-used cells: " + geohash);
        }
        assertFalse(blocked.isEmpty(), "Should still find available cells when some are blocked");
    }

    @Test
    void geoJsonFeatureCollectionUsesBlockTiling() throws Exception {
        String geojson = """
                {
                  "type": "FeatureCollection",
                  "features": [{
                    "type": "Feature",
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[
                        [-83.04713754413898, 42.32818024508649],
                        [-83.04713759685912, 42.330921520597414],
                        [-83.04358678933497, 42.330921521689845],
                        [-83.04358684205512, 42.32818024399414],
                        [-83.04713754413898, 42.32818024508649]
                      ]]
                    },
                    "properties": {}
                  }]
                }
                """;

        List<String> geohashes = geohashUtils.extractGeohashesFromGeoJSON(geojson, GRID_PRECISION, Set.of());

        assertTrue(geohashes.size() >= geofenceProperties.getLimits().getMinGeohashes());
        assertTrue(minChebyshevSeparation(geohashes) >= MIN_CELL_SEPARATION);
    }

    @Test
    void emptyFeatureCollectionThrowsNoAvailableGeohash() {
        GeofenceFeatureCollection empty = new GeofenceFeatureCollection();
        empty.setType("FeatureCollection");
        empty.setFeatures(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                geohashUtils.extractGeohashesFromGeofenceFeatureCollection(empty, GRID_PRECISION, Set.of()));
        assertTrue(ex.getCause() instanceof NoAvailableGeohashException);
    }

    @Test
    void boundingBoxUsesSouthNorthWestEastOrder() {
        // ch.hsr.geohash.BoundingBox expects (south, north, west, east).
        // Wrong order (south, west, north, east) throws for typical WGS84 envelopes.
        double south = 42.26067491606639;
        double north = 42.266997709373015;
        double west = -83.29028279020775;
        double east = -83.24876570513597;

        BoundingBox bbox = new BoundingBox(south, north, west, east);
        assertTrue(bbox.getSouthLatitude() < bbox.getNorthLatitude());
        assertTrue(bbox.getWestLongitude() < bbox.getEastLongitude());
    }

    private List<String> extract(double[][] ring) {
        return geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                polygonFeatureCollection(ring), GRID_PRECISION, Set.of());
    }

    private GeofenceFeatureCollection polygonFeatureCollection(double[][] ring) {
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

    private long minChebyshevSeparation(List<String> geohashes) {
        if (geohashes.size() < 2) {
            return Long.MAX_VALUE;
        }

        double latStep = latStep(GRID_PRECISION);
        double lonStep = lonStep(GRID_PRECISION);
        long minSeparation = Long.MAX_VALUE;

        for (int i = 0; i < geohashes.size(); i++) {
            long[] a = cellIndices(geohashes.get(i), latStep, lonStep);
            for (int j = i + 1; j < geohashes.size(); j++) {
                long[] b = cellIndices(geohashes.get(j), latStep, lonStep);
                long separation = Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
                minSeparation = Math.min(minSeparation, separation);
            }
        }
        return minSeparation;
    }

    private long[] cellIndices(String geohash, double latStep, double lonStep) {
        BoundingBox bb = GeoHash.fromGeohashString(geohash).getBoundingBox();
        long latIdx = (long) Math.floor((bb.getSouthLatitude() + 90.0) / latStep);
        long lonIdx = (long) Math.floor((bb.getWestLongitude() + 180.0) / lonStep);
        return new long[] { latIdx, lonIdx };
    }

    private double latStep(int precision) {
        int bits = precision * 5;
        int latBits = bits / 2;
        return 90.0 / Math.pow(2, latBits);
    }

    private double lonStep(int precision) {
        int bits = precision * 5;
        int lonBits = (bits + 1) / 2;
        return 180.0 / Math.pow(2, lonBits);
    }
}
