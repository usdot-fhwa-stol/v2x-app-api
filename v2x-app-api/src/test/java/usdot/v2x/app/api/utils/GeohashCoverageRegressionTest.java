package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.exceptions.GeofenceTooLargeException;
import usdot.v2x.app.api.exceptions.NoAvailableGeohashException;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.etx.configuration.geometry.Polygon;
import usdot.v2x.app.api.services.ErrorLoggingService;

class GeohashCoverageRegressionTest {

    private static final int PRECISION = 7;
    private static final int PATH_PRECISION = 6;
    /** Representatives must not be adjacent; overlapping 3×3s may sit 2 cells apart. */
    private static final int REPRESENTATIVE_CELL_SEPARATION = 2;
    private static final int COVERAGE_RADIUS = 1;
    private static final int PRIMARY_BLOCK_SIZE = 3;
    private static final double[][] OVERSIZED_TRIANGLE = {
            {-106.43815146211979, 40.71355603945426},
            {-105.33535432407251, 42.56947481806128},
            {-107.90265452163949, 42.41969732324517},
            {-106.43815146211979, 40.71355603945426}
    };
    private static final double[][] NARROW_TRIANGLE = {
            {-83.0502, 42.3202},
            {-83.0442, 42.3238},
            {-83.0442, 42.3166},
            {-83.0502, 42.3202}
    };
    private static final double[][] RECTANGLE = {
            {-83.0500, 42.3180},
            {-83.0500, 42.3220},
            {-83.0440, 42.3220},
            {-83.0440, 42.3180},
            {-83.0500, 42.3180}
    };

    private GeofenceProperties properties;
    private GeohashUtils geohashUtils;

    @BeforeEach
    void setUp() {
        properties = new GeofenceProperties();
        properties.getLimits().setMaxCandidateCells(10_000);
        properties.getLimits().setMaxGeohashes(500);
        properties.getLimits().setMinGeohashes(1);
        properties.getLimits().setAllowOverlappingGeohashes(true);
        properties.getLimits().setRepresentativeFilterThreshold(0);

        geohashUtils = new GeohashUtils(
                new ObjectMapper(),
                mock(ErrorLoggingService.class),
                properties,
                mock(JdbcTemplate.class));
    }

    @Test
    void suppliedOversizedOverrideIsRejectedWithValidationResponse() {
        GeofenceTooLargeException exception = assertThrows(GeofenceTooLargeException.class,
                () -> extract(OVERSIZED_TRIANGLE));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
        assertEquals("GEOFENCE_TOO_LARGE", exception.getErrorResponse().getError());
        assertTrue(exception.getErrorResponse().getDescription().contains("candidate cells"));
        assertTrue(exception.getErrorResponse().getDescription().contains("max-candidate-cells"));
    }

    @Test
    void equivalentOversizedTimDerivedRegionUsesTheSameGuard() throws Exception {
        String timDerivedGeoJson = new ObjectMapper().writeValueAsString(Map.of(
                "type", "Polygon",
                "coordinates", List.of(toCoordinateLists(OVERSIZED_TRIANGLE))));

        GeofenceTooLargeException exception = assertThrows(GeofenceTooLargeException.class,
                () -> geohashUtils.extractGeohashesFromGeoJSON(timDerivedGeoJson, PRECISION, Set.of()));

        assertEquals("GEOFENCE_TOO_LARGE", exception.getErrorResponse().getError());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
    }

    @Test
    void validRegionAtConfiguredSelectedLimitStillSucceeds() {
        List<String> baseline = extract(RECTANGLE);
        assertFalse(baseline.isEmpty());

        properties.getLimits().setMaxGeohashes(baseline.size());
        List<String> atLimit = assertDoesNotThrow(() -> extract(RECTANGLE));

        assertEquals(baseline.size(), atLimit.size());
    }

    @Test
    void validRegionAtConfiguredCandidateLimitStillSucceeds() {
        BoundingBox rectangleBounds = new BoundingBox(
                42.3180, 42.3220, -83.0500, -83.0440);
        long exactCandidateCount = GeohashScanBudget.estimateCandidateCellCount(
                rectangleBounds, PRECISION);
        properties.getLimits().setMaxCandidateCells(exactCandidateCount);

        assertFalse(assertDoesNotThrow(() -> extract(RECTANGLE)).isEmpty());
    }

    @Test
    void occupiedInteriorCellCanBeCoveredByAnAlreadyCoveredNeighbor() {
        properties.getLimits().setAllowOverlappingGeohashes(false);
        properties.getLimits().setRepresentativeFilterThreshold(20);
        double step = 180.0 / (1L << 17);
        double lat = -90.0 + (96234 + 0.5) * step;
        double lon = -180.0 + (70002 + 0.5) * step;
        double[][] corridor = thinGridCorridor(lon, lat, step);
        String occupied = GeoHash.withCharacterPrecision(lat, lon + 2 * step, PRECISION).toBase32();

        List<String> selected = geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                polygonFeatureCollection(corridor), PRECISION, Set.of(occupied));

        assertFalse(selected.contains(occupied));
        assertCoverage(selected, extractAll(corridor, PRECISION), PRECISION);

        properties.getLimits().setMaxGeohashes(selected.size() - 1);
        assertThrows(GeofenceTooLargeException.class,
                () -> geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                        polygonFeatureCollection(corridor), PRECISION, Set.of(occupied)));
    }

    @Test
    void saturatedInteriorCellsRejectRatherThanReturnPartialCoverage() {
        properties.getLimits().setAllowOverlappingGeohashes(false);
        properties.getLimits().setRepresentativeFilterThreshold(20);
        double step = 180.0 / (1L << 17);
        double lat = -90.0 + (96234 + 0.5) * step;
        double lon = -180.0 + (70002 + 0.5) * step;
        Set<String> occupied = new HashSet<>();
        for (int offset : List.of(1, 2, 3)) {
            occupied.add(GeoHash.withCharacterPrecision(lat, lon + offset * step, PRECISION).toBase32());
        }

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                        polygonFeatureCollection(thinGridCorridor(lon, lat, step)), PRECISION, occupied));

        assertTrue(exception instanceof NoAvailableGeohashException
                || exception.getCause() instanceof NoAvailableGeohashException);
    }

    @Test
    void gridAlignedPolygonIncludesEveryTouchingCellAndAvailableBoundaryAlternatives() {
        BoundingBox bounds = GeoHash.fromGeohashString("dpkrgdd").getBoundingBox();
        GeofenceFeatureCollection polygon = polygonFeatureCollection(ringFromBounds(bounds));
        Set<String> expected = touchingGridCells(bounds);
        assertEquals(9, expected.size());
        assertEquals(expected, new HashSet<>(extractAll(polygon, PRECISION)));

        Set<String> occupied = Set.of("dpkrgdg", "dpkrgdf", "dpkrgde", "dpkrgdd");
        properties.getLimits().setAllowOverlappingGeohashes(false);
        expected.removeAll(occupied);
        assertEquals(expected, new HashSet<>(geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                polygon, PRECISION, occupied, false)));
    }

    @Test
    void touchingBoundaryCellsAreReservedBeforeScanning() {
        double[][] ring = ringFromBounds(GeoHash.fromGeohashString("dpkrgdd").getBoundingBox());
        properties.getLimits().setMaxCandidateCells(9);
        assertEquals(9, assertDoesNotThrow(() -> extractAll(ring, PRECISION)).size());

        properties.getLimits().setMaxCandidateCells(8);
        GeofenceTooLargeException exception = assertThrows(GeofenceTooLargeException.class,
                () -> extractAll(ring, PRECISION));
        assertTrue(exception.getMessage().contains("GEOFENCE_TOO_LARGE"));
    }

    @Test
    void boundaryScanAndBudgetAreClippedAtWorldEdges() {
        for (double[] point : new double[][] {
                {-179.9999, 10.0}, {10.0, 89.9999}, {-179.9999, -89.9999}, {179.9999, 89.9999}
        }) {
            BoundingBox bounds = GeoHash.withCharacterPrecision(point[1], point[0], PRECISION).getBoundingBox();
            Set<String> expected = touchingGridCells(bounds);
            properties.getLimits().setMaxCandidateCells(expected.size());

            assertEquals(expected, new HashSet<>(assertDoesNotThrow(
                    () -> extractAll(ringFromBounds(bounds), PRECISION))));
        }
    }

    @Test
    void smallScanAcrossEquatorAndPrimeMeridianStaysWithinItsCellBudget() {
        BoundingBox bounds = GeoHash.withCharacterPrecision(0.0, 0.0, PRECISION).getBoundingBox();
        Set<String> expected = touchingGridCells(bounds);
        properties.getLimits().setMaxCandidateCells(expected.size());

        assertEquals(expected, new HashSet<>(extractAll(ringFromBounds(bounds), PRECISION)));
    }

    private static double[][] thinGridCorridor(double lon, double lat, double step) {
        return new double[][] {
                {lon, lat - step * 0.1}, {lon + 24 * step, lat - step * 0.1},
                {lon + 24 * step, lat + step * 0.1}, {lon, lat + step * 0.1},
                {lon, lat - step * 0.1}
        };
    }

    private static double[][] ringFromBounds(BoundingBox bounds) {
        return new double[][] {
                {bounds.getWestLongitude(), bounds.getSouthLatitude()},
                {bounds.getEastLongitude(), bounds.getSouthLatitude()},
                {bounds.getEastLongitude(), bounds.getNorthLatitude()},
                {bounds.getWestLongitude(), bounds.getNorthLatitude()},
                {bounds.getWestLongitude(), bounds.getSouthLatitude()}
        };
    }

    private static Set<String> touchingGridCells(BoundingBox bounds) {
        Set<String> cells = new HashSet<>();
        double latStep = bounds.getNorthLatitude() - bounds.getSouthLatitude();
        double lonStep = bounds.getEastLongitude() - bounds.getWestLongitude();
        double latCenter = (bounds.getNorthLatitude() + bounds.getSouthLatitude()) / 2;
        double lonCenter = (bounds.getEastLongitude() + bounds.getWestLongitude()) / 2;
        for (int row = -1; row <= 1; row++) {
            for (int column = -1; column <= 1; column++) {
                double lat = latCenter + row * latStep;
                double lon = lonCenter + column * lonStep;
                if (lat > -90 && lat < 90 && lon > -180 && lon < 180) {
                    cells.add(GeoHash.withCharacterPrecision(lat, lon, PRECISION).toBase32());
                }
            }
        }
        return cells;
    }

    @Test
    void fragmentedGeometryStopsWhenCoverageFillExceedsSelectedLimit() throws Exception {
        List<String> cornerCells = cellsAtCornersOfOnePrimaryBlock();
        String fragmentedMultiPolygon = new ObjectMapper().writeValueAsString(Map.of(
                "type", "MultiPolygon",
                "coordinates", cornerCells.stream()
                        .map(this::smallPolygonInsideCell)
                        .map(List::of)
                        .toList()));
        properties.getLimits().setMaxGeohashes(2);

        GeofenceTooLargeException exception = assertThrows(GeofenceTooLargeException.class,
                () -> geohashUtils.extractGeohashesFromGeoJSON(
                        fragmentedMultiPolygon, PRECISION, Set.of()));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
        assertEquals("GEOFENCE_TOO_LARGE", exception.getErrorResponse().getError());
        assertTrue(exception.getErrorResponse().getDescription().contains("selected geohash count 3"));
    }

    @Test
    void narrowTriangleIncludesTheCellContainingEveryTip() {
        List<String> selected = extract(NARROW_TRIANGLE);

        for (int i = 0; i < NARROW_TRIANGLE.length - 1; i++) {
            double[] tip = NARROW_TRIANGLE[i];
            String tipCell = GeoHash.withCharacterPrecision(tip[1], tip[0], PRECISION).toBase32();
            assertTrue(selected.contains(tipCell), "Missing tip cell " + tipCell + " from " + selected);
        }
    }

    @Test
    void boundingBoxCellOutsideTriangleIsExcluded() {
        List<String> selected = extract(NARROW_TRIANGLE);
        // East of the long-edge midpoint, inside the bbox but not next to a tip.
        String outsideCell = GeoHash.withCharacterPrecision(42.3202, -83.0465, PRECISION).toBase32();

        assertFalse(endpointCluster(NARROW_TRIANGLE, PRECISION).contains(outsideCell),
                "Fixture must not be an endpoint cell: " + outsideCell);
        assertFalse(selected.contains(outsideCell),
                "A cell inside the bounding box but outside the triangle must not be selected");
    }

    @Test
    void rectangleStillReturnsOnlyIntersectingCells() {
        List<String> selected = extract(RECTANGLE);
        org.locationtech.jts.geom.Polygon rectangle = jtsPolygon(RECTANGLE);

        assertFalse(selected.isEmpty());
        for (String geohash : selected) {
            assertTrue(rectangle.intersects(cellPolygon(geohash)),
                    "Selected rectangle cell does not intersect the deployment polygon: " + geohash);
        }
        assertEveryIntersectingCellIsCovered(selected, extractAll(RECTANGLE, PRECISION), PRECISION);
        assertTrue(minChebyshevSeparation(selected, PRECISION) >= REPRESENTATIVE_CELL_SEPARATION,
                "Rectangle representatives must not be adjacent: " + selected);
    }

    @Test
    void syntheticPrecisionSixPathUsesUniformThreeByThreeTiling() {
        double[][] corridorRing = {
                {10.000, 10.000}, {10.060, 10.020}, {10.060, 10.023},
                {10.000, 10.003}, {10.000, 10.000}
        };
        List<String> selected = extract(corridorRing, PATH_PRECISION);
        Set<String> selectedSet = new HashSet<>(selected);
        org.locationtech.jts.geom.Polygon corridor = jtsPolygon(corridorRing);

        assertFalse(selected.isEmpty(), "Synthetic corridor should produce representatives");
        assertEquals(selected.size(), selectedSet.size(), "Representatives must remain unique");
        assertTrue(selected.size() <= properties.getLimits().getMaxGeohashes(),
                "Synthetic corridor must remain within the configured selected-geohash limit");
        for (String geohash : selected) {
            assertTrue(corridor.intersects(cellPolygon(geohash)),
                    "Selected cell does not intersect the deployment polygon: " + geohash);
        }
        assertTrue(minChebyshevSeparation(selected, PATH_PRECISION) >= REPRESENTATIVE_CELL_SEPARATION,
                "Representatives must not be adjacent: " + selected);
        assertEveryIntersectingCellIsCovered(selected, extractAll(corridorRing, PATH_PRECISION), PATH_PRECISION);

        for (String vertexCell : endpointCluster(corridorRing, PATH_PRECISION)) {
            for (GeoHash neighbor : GeoHash.fromGeohashString(vertexCell).getAdjacent()) {
                String hash = neighbor.toBase32();
                if (!corridor.intersects(cellPolygon(hash))) {
                    assertFalse(selectedSet.contains(hash),
                            "Non-intersecting neighbor should not be selected: " + hash);
                }
            }
        }
    }

    @Test
    void malformedPolygonKeepsExistingValidationBehavior() {
        double[][] unclosedRing = {
                {-83.0500, 42.3180},
                {-83.0480, 42.3220},
                {-83.0440, 42.3180}
        };

        RuntimeException exception = assertThrows(RuntimeException.class, () -> extract(unclosedRing));

        assertFalse(exception instanceof GeofenceTooLargeException);
        assertTrue(exception.getCause() instanceof NoAvailableGeohashException);
    }

    @Test
    void candidateEstimateIsPrecisionAwareAndGuardNeedsNoEnumeration() {
        BoundingBox oversizedBounds = new BoundingBox(
                40.71355603945426, 42.56947481806128,
                -107.90265452163949, -105.33535432407251);

        long precisionSix = GeohashScanBudget.estimateCandidateCellCount(oversizedBounds, 6);
        long precisionSeven = GeohashScanBudget.estimateCandidateCellCount(oversizedBounds, 7);
        assertTrue(precisionSeven > precisionSix);

        GeohashScanBudget budget = new GeohashScanBudget(10);
        GeofenceTooLargeException exception = assertThrows(GeofenceTooLargeException.class,
                () -> budget.reserve(oversizedBounds, PRECISION));
        assertNotEquals(0, exception.getErrorResponse().getDescription().length());
    }

    @Test
    void multipolygonUsesTheSameComponentBudgetAsEquivalentFeatures() throws Exception {
        double[][] first = {
                {-83.0500, 42.3180},
                {-83.0500, 42.3220},
                {-83.0460, 42.3220},
                {-83.0460, 42.3180},
                {-83.0500, 42.3180}
        };
        double[][] second = {
                {-82.0500, 42.3180},
                {-82.0500, 42.3220},
                {-82.0460, 42.3220},
                {-82.0460, 42.3180},
                {-82.0500, 42.3180}
        };
        long componentBudget = GeohashScanBudget.estimateCandidateCellCount(
                new BoundingBox(42.3180, 42.3220, -83.0500, -83.0460), PRECISION)
                + GeohashScanBudget.estimateCandidateCellCount(
                        new BoundingBox(42.3180, 42.3220, -82.0500, -82.0460), PRECISION);
        properties.getLimits().setMaxCandidateCells(componentBudget);

        ObjectMapper mapper = new ObjectMapper();
        String multiPolygon = mapper.writeValueAsString(Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(toCoordinateLists(first)),
                        List.of(toCoordinateLists(second)))));
        String separateFeatures = mapper.writeValueAsString(Map.of(
                "type", "FeatureCollection",
                "features", List.of(
                        Map.of("type", "Feature", "geometry", Map.of(
                                "type", "Polygon", "coordinates", List.of(toCoordinateLists(first)))),
                        Map.of("type", "Feature", "geometry", Map.of(
                                "type", "Polygon", "coordinates", List.of(toCoordinateLists(second)))))));

        List<String> expected = geohashUtils.extractGeohashesFromGeoJSON(
                separateFeatures, PRECISION, Set.of());
        List<String> actual = assertDoesNotThrow(() -> geohashUtils.extractGeohashesFromGeoJSON(
                multiPolygon, PRECISION, Set.of()));

        assertEquals(Set.copyOf(expected), Set.copyOf(actual));
    }

    @Test
    void boundedParallelScanMatchesSequentialCoverage() throws Exception {
        double[][] first = {
                {-83.1000, 42.3000},
                {-83.1000, 42.3500},
                {-83.0200, 42.3500},
                {-83.0200, 42.3000},
                {-83.1000, 42.3000}
        };
        double[][] second = {
                {-82.1000, 42.3000},
                {-82.1000, 42.3500},
                {-82.0200, 42.3500},
                {-82.0200, 42.3000},
                {-82.1000, 42.3000}
        };
        String multiPolygon = new ObjectMapper().writeValueAsString(Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(toCoordinateLists(first)),
                        List.of(toCoordinateLists(second)))));

        GeohashUtils sequential = configuredGeohashUtils(1);
        GeohashUtils parallel = configuredGeohashUtils(4);
        try {
            List<String> expected = sequential.extractGeohashesFromGeoJSON(
                    multiPolygon, PRECISION, Set.of());
            List<String> actual = parallel.extractGeohashesFromGeoJSON(
                    multiPolygon, PRECISION, Set.of());

            assertEquals(expected, actual);
        } finally {
            sequential.shutdownPolygonAnalysisExecutor();
            parallel.shutdownPolygonAnalysisExecutor();
        }
    }

    @Test
    void polygonAnalysisParallelismMustBePositive() {
        GeofenceProperties invalid = new GeofenceProperties();
        invalid.getGeohash().setPolygonAnalysisParallelism(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new GeohashUtils(
                        new ObjectMapper(), mock(ErrorLoggingService.class), invalid, mock(JdbcTemplate.class)));

        assertTrue(exception.getMessage().contains("polygon-analysis-parallelism"));
    }

    @Test
    void syntheticPolygonsCoverEveryIntersectingLevelSevenCell() {
        assertCoverageForThresholds("wide rectangle", polygonFeatureCollection(new double[][] {
                {10.100, 10.100}, {10.100, 10.116}, {10.112, 10.116},
                {10.112, 10.100}, {10.100, 10.100}
        }));
        assertCoverageForThresholds("diagonal corridor", polygonFeatureCollection(new double[][] {
                {10.100, 10.100}, {10.116, 10.116}, {10.118, 10.114},
                {10.102, 10.098}, {10.100, 10.100}
        }));
        assertCoverageForThresholds("narrow zigzag", polygonFeatureCollection(new double[][] {
                {10.100, 10.100}, {10.108, 10.101}, {10.114, 10.106},
                {10.113, 10.108}, {10.107, 10.103}, {10.100, 10.102},
                {10.100, 10.100}
        }));
    }

    @Test
    void syntheticCorridorsCoverIntersectingCellsAfterRepresentativeThinning() {
        assertSyntheticCorridorCovers("thin east-west corridor", new double[][] {
                {10.000, 10.000}, {10.180, 10.012}, {10.180, 10.020},
                {10.000, 10.008}, {10.000, 10.000}
        });
        assertSyntheticCorridorCovers("diagonal corridor", new double[][] {
                {9.9965, 10.0035}, {10.1165, 10.1235}, {10.1235, 10.1165},
                {10.0035, 9.9965}, {9.9965, 10.0035}
        });
        assertSyntheticCorridorCovers("north-south corridor", new double[][] {
                {10.000, 10.000}, {10.008, 10.000}, {10.008, 10.180},
                {10.000, 10.180}, {10.000, 10.000}
        });
    }

    private void assertSyntheticCorridorCovers(String name, double[][] ring) {
        List<String> intersecting = extractAll(ring, PATH_PRECISION);
        List<String> selected = extract(ring, PATH_PRECISION);

        assertTrue(intersecting.size() > 20,
                name + " should exceed the representative threshold, intersecting=" + intersecting.size());
        assertTrue(selected.size() < intersecting.size(),
                name + " should be thinned, selected=" + selected.size() + " intersecting=" + intersecting.size());
        assertCoverage(selected, intersecting, PATH_PRECISION);
        assertTrue(minChebyshevSeparation(selected, PATH_PRECISION) >= REPRESENTATIVE_CELL_SEPARATION,
                name + " representatives must not be adjacent: " + selected);
    }

    private void assertCoverageForThresholds(String name, GeofenceFeatureCollection collection) {
        List<String> intersecting = extractAll(collection, PRECISION);
        assertFalse(intersecting.isEmpty(), name + " should have intersecting cells");

        properties.getLimits().setRepresentativeFilterThreshold(0);
        assertCoverage(extract(collection, PRECISION), intersecting, PRECISION);

        properties.getLimits().setRepresentativeFilterThreshold(20);
        List<String> selectedAtDefaultThreshold = extract(collection, PRECISION);
        assertTrue(new HashSet<>(intersecting).containsAll(selectedAtDefaultThreshold),
                name + " representatives must remain a subset of intersecting cells");
        assertCoverage(selectedAtDefaultThreshold, intersecting, PRECISION);
        if (selectedAtDefaultThreshold.size() >= 2) {
            assertTrue(minChebyshevSeparation(selectedAtDefaultThreshold, PRECISION) >= REPRESENTATIVE_CELL_SEPARATION,
                    name + " representatives must not be adjacent: " + selectedAtDefaultThreshold);
        }
    }

    private GeohashUtils configuredGeohashUtils(int parallelism) {
        GeofenceProperties configured = new GeofenceProperties();
        configured.getGeohash().setPolygonAnalysisParallelism(parallelism);
        configured.getLimits().setMaxCandidateCells(10_000);
        configured.getLimits().setMaxGeohashes(1_000);
        configured.getLimits().setMinGeohashes(1);
        configured.getLimits().setAllowOverlappingGeohashes(true);
        configured.getLimits().setRepresentativeFilterThreshold(0);
        return new GeohashUtils(
                new ObjectMapper(), mock(ErrorLoggingService.class), configured, mock(JdbcTemplate.class));
    }

    private List<String> extract(double[][] ring) {
        return extract(ring, PRECISION);
    }

    private List<String> extract(double[][] ring, int precision) {
        return extract(polygonFeatureCollection(ring), precision);
    }

    private List<String> extract(GeofenceFeatureCollection collection, int precision) {
        return geohashUtils.extractGeohashesFromGeofenceFeatureCollection(collection, precision, Set.of());
    }

    private List<String> extractAll(double[][] ring, int precision) {
        return extractAll(polygonFeatureCollection(ring), precision);
    }

    private List<String> extractAll(GeofenceFeatureCollection collection, int precision) {
        return geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                collection, precision, Set.of(), false);
    }

    private void assertEveryIntersectingCellIsCovered(
            List<String> selected, List<String> intersecting, int precision) {
        assertCoverage(selected, intersecting, precision);
    }

    private void assertCoverage(List<String> selected, List<String> intersecting, int precision) {
        assertTrue(new HashSet<>(intersecting).containsAll(selected),
                "Representatives must remain a subset of intersecting cells: " + selected);
        Set<String> uncovered = uncoveredIntersectingCells(selected, intersecting, precision);
        assertTrue(uncovered.isEmpty(),
                "Every intersecting L7 cell must sit in some selected 3×3, uncovered=" + uncovered
                        + " selected=" + selected);
    }

    private Set<String> uncoveredIntersectingCells(
            List<String> selected, List<String> intersecting, int precision) {
        Set<String> uncovered = new HashSet<>();
        for (String cell : intersecting) {
            long[] idx = cellIndices(cell, precision);
            boolean covered = false;
            for (String representative : selected) {
                if (chebyshevDistance(idx, cellIndices(representative, precision)) <= COVERAGE_RADIUS) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                uncovered.add(cell);
            }
        }
        return uncovered;
    }

    private GeofenceFeatureCollection polygonFeatureCollection(double[][] ring) {
        Polygon polygon = new Polygon();
        polygon.setCoordinates(List.of(toCoordinateLists(ring)));

        GeofenceFeature feature = new GeofenceFeature();
        feature.setType("Feature");
        feature.setGeometry(polygon);
        feature.setProperties(Map.of());

        GeofenceFeatureCollection collection = new GeofenceFeatureCollection();
        collection.setType("FeatureCollection");
        collection.setFeatures(List.of(feature));
        return collection;
    }

    private List<String> cellsAtCornersOfOnePrimaryBlock() {
        int totalBits = PRECISION * 5;
        double latStep = 180.0 / Math.pow(2, totalBits / 2);
        double lonStep = 360.0 / Math.pow(2, (totalBits + 1) / 2);
        GeoHash initial = GeoHash.withCharacterPrecision(42.3200, -83.0470, PRECISION);
        BoundingBox initialBounds = initial.getBoundingBox();
        long initialLatIndex = (long) Math.floor((initialBounds.getSouthLatitude() + 90.0) / latStep);
        long initialLonIndex = (long) Math.floor((initialBounds.getWestLongitude() + 180.0) / lonStep);
        long latOffset = Math.floorMod(-initialLatIndex, PRIMARY_BLOCK_SIZE);
        long lonOffset = Math.floorMod(-initialLonIndex, PRIMARY_BLOCK_SIZE);
        double baseLat = initial.getBoundingBoxCenter().getLatitude() + latOffset * latStep;
        double baseLon = initial.getBoundingBoxCenter().getLongitude() + lonOffset * lonStep;

        List<String> cells = new ArrayList<>();
        for (int latitudeOffset : List.of(0, 2)) {
            for (int longitudeOffset : List.of(0, 2)) {
                cells.add(GeoHash.withCharacterPrecision(
                        baseLat + latitudeOffset * latStep,
                        baseLon + longitudeOffset * lonStep,
                        PRECISION).toBase32());
            }
        }
        return cells;
    }

    private List<List<Double>> smallPolygonInsideCell(String geohash) {
        GeoHash cell = GeoHash.fromGeohashString(geohash);
        BoundingBox bounds = cell.getBoundingBox();
        double latRadius = (bounds.getNorthLatitude() - bounds.getSouthLatitude()) / 10.0;
        double lonRadius = (bounds.getEastLongitude() - bounds.getWestLongitude()) / 10.0;
        double lat = cell.getBoundingBoxCenter().getLatitude();
        double lon = cell.getBoundingBoxCenter().getLongitude();
        return List.of(
                List.of(lon - lonRadius, lat - latRadius),
                List.of(lon + lonRadius, lat - latRadius),
                List.of(lon + lonRadius, lat + latRadius),
                List.of(lon - lonRadius, lat + latRadius),
                List.of(lon - lonRadius, lat - latRadius));
    }

    private List<List<Double>> toCoordinateLists(double[][] ring) {
        List<List<Double>> coordinates = new ArrayList<>();
        for (double[] point : ring) {
            coordinates.add(List.of(point[0], point[1]));
        }
        return coordinates;
    }

    private org.locationtech.jts.geom.Polygon jtsPolygon(double[][] ring) {
        Coordinate[] coordinates = new Coordinate[ring.length];
        for (int i = 0; i < ring.length; i++) {
            coordinates[i] = new Coordinate(ring[i][0], ring[i][1]);
        }
        return new GeometryFactory().createPolygon(coordinates);
    }

    private Set<String> endpointCluster(double[][] ring, int precision) {
        double[] first = null;
        double[] second = null;
        double best = -1;
        for (int i = 0; i < ring.length; i++) {
            for (int j = i + 1; j < ring.length; j++) {
                double dx = ring[i][0] - ring[j][0];
                double dy = ring[i][1] - ring[j][1];
                double distanceSq = dx * dx + dy * dy;
                if (distanceSq > best) {
                    best = distanceSq;
                    first = ring[i];
                    second = ring[j];
                }
            }
        }
        Set<String> cluster = new HashSet<>();
        if (first != null) {
            cluster.add(GeoHash.withCharacterPrecision(first[1], first[0], precision).toBase32());
        }
        if (second != null) {
            cluster.add(GeoHash.withCharacterPrecision(second[1], second[0], precision).toBase32());
        }
        return cluster;
    }

    private org.locationtech.jts.geom.Polygon cellPolygon(String geohash) {
        BoundingBox bounds = GeoHash.fromGeohashString(geohash).getBoundingBox();
        return new GeometryFactory().createPolygon(new Coordinate[] {
                new Coordinate(bounds.getWestLongitude(), bounds.getSouthLatitude()),
                new Coordinate(bounds.getEastLongitude(), bounds.getSouthLatitude()),
                new Coordinate(bounds.getEastLongitude(), bounds.getNorthLatitude()),
                new Coordinate(bounds.getWestLongitude(), bounds.getNorthLatitude()),
                new Coordinate(bounds.getWestLongitude(), bounds.getSouthLatitude())
        });
    }

    private long minChebyshevSeparation(List<String> geohashes, int precision) {
        long minimum = Long.MAX_VALUE;
        for (int i = 0; i < geohashes.size(); i++) {
            long[] first = cellIndices(geohashes.get(i), precision);
            for (int j = i + 1; j < geohashes.size(); j++) {
                minimum = Math.min(minimum,
                        chebyshevDistance(first, cellIndices(geohashes.get(j), precision)));
            }
        }
        return minimum;
    }

    private long[] cellIndices(String geohash, int precision) {
        BoundingBox bounds = GeoHash.fromGeohashString(geohash).getBoundingBox();
        int bits = precision * 5;
        double latStep = 180.0 / Math.pow(2, bits / 2);
        double lonStep = 360.0 / Math.pow(2, (bits + 1) / 2);
        return new long[] {
                (long) Math.floor((bounds.getSouthLatitude() + 90.0) / latStep),
                (long) Math.floor((bounds.getWestLongitude() + 180.0) / lonStep)
        };
    }

    private long chebyshevDistance(long[] first, long[] second) {
        return Math.max(Math.abs(first[0] - second[0]), Math.abs(first[1] - second[1]));
    }
}
