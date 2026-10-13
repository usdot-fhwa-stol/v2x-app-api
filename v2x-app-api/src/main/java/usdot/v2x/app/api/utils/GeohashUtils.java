package usdot.v2x.app.api.utils;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.services.ErrorLoggingService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import usdot.v2x.app.api.exceptions.NoAvailableGeohashException;
import usdot.v2x.app.api.exceptions.GeofenceTooLargeException;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.spatial4j.distance.DistanceUtils;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for geohash operations and geospatial data processing.
 */
@Component
@Slf4j
public class GeohashUtils {

    /**
     * One representative per this many cells in each direction (3×3 coverage zone).
     * Subscribers receive messages for their cell + all 8 neighbors (Chebyshev
     * radius 1), so a block of 3 is the largest tile where a single center
     * representative is guaranteed to reach every subscriber in that tile.
     */
    private static final int PRIMARY_BLOCK_SIZE = 3;
    /**
     * Chebyshev distance at which a selected representative covers a subscriber
     * cell (own cell + 8 neighbors). Spacing of {@link #PRIMARY_BLOCK_SIZE} is
     * preferred; {@code COVERAGE_RADIUS + 1} is allowed when a hole would remain.
     */
    private static final int COVERAGE_RADIUS = 1;
    /**
     * Minimum cell-index separation when topping up small regions to minGeohashes.
     * Must be >= PRIMARY_BLOCK_SIZE to prevent supplement cells from landing inside
     * the coverage zone of an existing representative.
     */
    private static final int SUPPLEMENT_MIN_CELL_SEPARATION = 3;
    /** Executor coordination is slower than a direct scan below this work size. */
    private static final long PARALLEL_SCAN_MIN_CANDIDATE_CELLS = 4_096;

    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory;
    private final ErrorLoggingService errorLoggingService;
    private final GeofenceProperties geofenceProperties;
    private final JdbcTemplate jdbcTemplate;
    private final int polygonAnalysisParallelism;
    private final ExecutorService polygonAnalysisExecutor;

    public GeohashUtils(ObjectMapper objectMapper, ErrorLoggingService errorLoggingService,
            GeofenceProperties geofenceProperties, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.geometryFactory = new GeometryFactory();
        this.errorLoggingService = errorLoggingService;
        this.geofenceProperties = geofenceProperties;
        this.jdbcTemplate = jdbcTemplate;
        int configuredParallelism = geofenceProperties.getGeohash().getPolygonAnalysisParallelism();
        if (configuredParallelism <= 0) {
            throw new IllegalStateException("geofence.geohash.polygon-analysis-parallelism must be positive");
        }
        this.polygonAnalysisParallelism = Math.min(
                configuredParallelism, Math.max(1, Runtime.getRuntime().availableProcessors()));
        this.polygonAnalysisExecutor = polygonAnalysisParallelism > 1
                ? Executors.newFixedThreadPool(
                        polygonAnalysisParallelism,
                        Thread.ofPlatform().daemon().name("geohash-polygon-analysis-", 0).factory())
                : null;
    }

    @PreDestroy
    public void shutdownPolygonAnalysisExecutor() {
        if (polygonAnalysisExecutor != null) {
            polygonAnalysisExecutor.shutdown();
        }
    }

    /**
     * Extract geohashes from GeoJSON data using ch.hsr.geohash library
     * 
     * @param geojsonString The GeoJSON string
     * @param precision     The geohash precision (default 7)
     * @return List of unique geohashes
     * @throws RuntimeException if geohash extraction fails
     */
    public List<String> extractGeohashesFromGeoJSON(String geojsonString, int precision) {
        try {
            JsonNode geojson = objectMapper.readTree(geojsonString);

            // Fetch active geohashes to avoid collisions with existing deployments
            Set<String> existingUsed = fetchActiveGeohashes();
            // Use the optimized approach by default
            return extractGeohashesInternal(geojson, precision, existingUsed);
        } catch (GeofenceTooLargeException e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = "Error extracting geohashes from GeoJSON: " + e.getMessage();
            log.error(errorMessage, e);

            // Log error to PostgreSQL
            errorLoggingService.logErrorFromRequest(
                    "GEOHASH_EXTRACTION_ERROR",
                    errorMessage,
                    getStackTraceAsString(e),
                    ErrorLoggingService.ErrorSeverity.HIGH);

            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Extract geohashes from GeoJSON data with default precision from configuration
     * 
     * @param geojsonString The GeoJSON string
     * @return List of unique geohashes
     * @throws RuntimeException if geohash extraction fails
     */
    public List<String> extractGeohashesFromGeoJSON(String geojsonString) {
        return extractGeohashesFromGeoJSON(geojsonString, geofenceProperties.getGeohash().getPrecision());
    }

    /**
     * Extract geohashes from GeoJSON with a set of already-used geohashes to avoid
     * collisions
     */
    public List<String> extractGeohashesFromGeoJSON(String geojsonString, int precision,
            Set<String> existingUsedGeohashes) {
        try {
            JsonNode geojson = objectMapper.readTree(geojsonString);

            // Use the optimized approach by default
            return extractGeohashesInternal(geojson, precision,
                    existingUsedGeohashes == null ? java.util.Collections.emptySet() : existingUsedGeohashes);
        } catch (GeofenceTooLargeException e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = "Error extracting geohashes from GeoJSON: " + e.getMessage();
            log.error(errorMessage, e);

            errorLoggingService.logErrorFromRequest(
                    "GEOHASH_EXTRACTION_ERROR",
                    errorMessage,
                    getStackTraceAsString(e),
                    ErrorLoggingService.ErrorSeverity.HIGH);

            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Extract geohashes from GeoJSON excluding geohashes of a specific geofenceId
     * (useful for update flows to avoid self-collision)
     */
    public List<String> extractGeohashesFromGeoJSON(String geojsonString, int precision, String geofenceIdToExclude) {
        try {
            JsonNode geojson = objectMapper.readTree(geojsonString);
            Set<String> existingUsed = fetchActiveGeohashesExcluding(geofenceIdToExclude);
            return extractGeohashesInternal(geojson, precision, existingUsed);
        } catch (GeofenceTooLargeException e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = "Error extracting geohashes from GeoJSON (exclude geofence): " + e.getMessage();
            log.error(errorMessage, e);
            errorLoggingService.logErrorFromRequest(
                    "GEOHASH_EXTRACTION_ERROR",
                    errorMessage,
                    getStackTraceAsString(e),
                    ErrorLoggingService.ErrorSeverity.HIGH);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Extract geohashes from GeofenceFeatureCollection POJO using ch.hsr.geohash
     * library
     * 
     * @param geofenceFeatureCollection The GeofenceFeatureCollection POJO
     * @param precision                 The geohash precision (default from
     *                                  configuration)
     * @return List of unique geohashes
     * @throws RuntimeException if geohash extraction fails
     */
    public List<String> extractGeohashesFromGeofenceFeatureCollection(
            GeofenceFeatureCollection geofenceFeatureCollection, int precision) {
        try {
            // Fetch active geohashes to avoid collisions with existing deployments
            Set<String> existingUsed = fetchActiveGeohashes();
            // Use the optimized approach by default
            return extractGeofenceFeatureCollectionInternal(geofenceFeatureCollection, precision,
                    existingUsed, true);
        } catch (GeofenceTooLargeException e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = "Error extracting geohashes from GeofenceFeatureCollection: " + e.getMessage();
            log.error(errorMessage, e);

            // Log error to PostgreSQL
            errorLoggingService.logErrorFromRequest(
                    "GEOHASH_EXTRACTION_ERROR",
                    errorMessage,
                    getStackTraceAsString(e),
                    ErrorLoggingService.ErrorSeverity.HIGH);

            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Extract geohashes from GeofenceFeatureCollection POJO with default precision
     * from configuration
     * 
     * @param geofenceFeatureCollection The GeofenceFeatureCollection POJO
     * @return List of unique geohashes
     * @throws RuntimeException if geohash extraction fails
     */
    public List<String> extractGeohashesFromGeofenceFeatureCollection(
            GeofenceFeatureCollection geofenceFeatureCollection, String geofenceId) {
        return extractGeohashesFromGeofenceFeatureCollection(geofenceFeatureCollection, geofenceId, true);
    }

    /**
     * Extract geohashes from GeofenceFeatureCollection POJO with default precision,
     * excluding an existing deployment's cells from collision checks.
     *
     * @param filterRepresentatives when {@code true} (default), thin to 3×3
     *                              representatives; when {@code false}, return
     *                              every intersecting cell that is still available
     */
    public List<String> extractGeohashesFromGeofenceFeatureCollection(
            GeofenceFeatureCollection geofenceFeatureCollection, String geofenceId,
            boolean filterRepresentatives) {
        Set<String> existingUsed = fetchActiveGeohashesExcluding(geofenceId);
        return extractGeofenceFeatureCollectionInternal(geofenceFeatureCollection,
                geofenceProperties.getGeohash().getPrecision(), existingUsed, filterRepresentatives);
    }

    /**
     * Extract geohashes from GeofenceFeatureCollection with a set of already-used
     * geohashes to avoid collisions
     */
    public List<String> extractGeohashesFromGeofenceFeatureCollection(
            GeofenceFeatureCollection geofenceFeatureCollection, int precision, Set<String> existingUsedGeohashes) {
        return extractGeohashesFromGeofenceFeatureCollection(
                geofenceFeatureCollection, precision, existingUsedGeohashes, true);
    }

    /**
     * Extract geohashes from GeofenceFeatureCollection with a set of already-used
     * geohashes to avoid collisions.
     *
     * @param filterRepresentatives when {@code true} (default), thin to 3×3
     *                              representatives; when {@code false}, return
     *                              every intersecting cell that is still available
     */
    public List<String> extractGeohashesFromGeofenceFeatureCollection(
            GeofenceFeatureCollection geofenceFeatureCollection, int precision, Set<String> existingUsedGeohashes,
            boolean filterRepresentatives) {
        try {
            return extractGeofenceFeatureCollectionInternal(geofenceFeatureCollection, precision,
                    existingUsedGeohashes == null ? java.util.Collections.emptySet() : existingUsedGeohashes,
                    filterRepresentatives);
        } catch (GeofenceTooLargeException e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = "Error extracting geohashes from GeofenceFeatureCollection: " + e.getMessage();
            log.error(errorMessage, e);

            errorLoggingService.logErrorFromRequest(
                    "GEOHASH_EXTRACTION_ERROR",
                    errorMessage,
                    getStackTraceAsString(e),
                    ErrorLoggingService.ErrorSeverity.HIGH);

            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Create JTS Polygon from custom Polygon POJO
     */
    private org.locationtech.jts.geom.Polygon createJtsPolygonFromCustomPolygon(
            usdot.v2x.app.api.models.etx.configuration.geometry.Polygon polygon) {
        try {
            if (polygon.getCoordinates() == null || polygon.getCoordinates().isEmpty()) {
                return null;
            }

            // Create exterior ring
            List<List<Double>> exteriorRing = polygon.getCoordinates().get(0);
            Coordinate[] exteriorCoords = new Coordinate[exteriorRing.size()];
            for (int i = 0; i < exteriorRing.size(); i++) {
                List<Double> coord = exteriorRing.get(i);
                if (coord.size() >= 2) {
                    exteriorCoords[i] = new Coordinate(coord.get(0), coord.get(1));
                }
            }
            LinearRing shell = geometryFactory.createLinearRing(exteriorCoords);

            // Create interior rings (holes) if any
            LinearRing[] holes = null;
            if (polygon.getCoordinates().size() > 1) {
                holes = new LinearRing[polygon.getCoordinates().size() - 1];
                for (int i = 1; i < polygon.getCoordinates().size(); i++) {
                    List<List<Double>> interiorRing = polygon.getCoordinates().get(i);
                    Coordinate[] interiorCoords = new Coordinate[interiorRing.size()];
                    for (int j = 0; j < interiorRing.size(); j++) {
                        List<Double> coord = interiorRing.get(j);
                        if (coord.size() >= 2) {
                            interiorCoords[j] = new Coordinate(coord.get(0), coord.get(1));
                        }
                    }
                    holes[i - 1] = geometryFactory.createLinearRing(interiorCoords);
                }
            }

            return geometryFactory.createPolygon(shell, holes);
        } catch (Exception e) {
            log.warn("Failed to create JTS Polygon from custom Polygon: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convert GeoJSON to JTS Geometry
     */
    private Geometry convertGeoJSONToJTS(JsonNode geometry) {
        String type = geometry.get("type").asText();
        JsonNode coordinates = geometry.get("coordinates");

        switch (type) {
            case "Point":
                return createPoint(coordinates);
            case "LineString":
                return createLineString(coordinates);
            case "Polygon":
                return createPolygon(coordinates);
            case "MultiPoint":
                return createMultiPoint(coordinates);
            case "MultiLineString":
                return createMultiLineString(coordinates);
            case "MultiPolygon":
                return createMultiPolygon(coordinates);
            default:
                log.warn("Unsupported geometry type: {}", type);
                return null;
        }
    }

    /**
     * Create JTS Point from coordinates
     */
    private org.locationtech.jts.geom.Point createPoint(JsonNode coordinates) {
        if (coordinates.isArray() && coordinates.size() >= 2) {
            double x = coordinates.get(0).asDouble();
            double y = coordinates.get(1).asDouble();
            return geometryFactory.createPoint(new Coordinate(x, y));
        }
        return null;
    }

    /**
     * Create JTS LineString from coordinates
     */
    private LineString createLineString(JsonNode coordinates) {
        if (coordinates.isArray()) {
            Coordinate[] coords = new Coordinate[coordinates.size()];
            for (int i = 0; i < coordinates.size(); i++) {
                JsonNode coord = coordinates.get(i);
                if (coord.isArray() && coord.size() >= 2) {
                    coords[i] = new Coordinate(coord.get(0).asDouble(), coord.get(1).asDouble());
                }
            }
            return geometryFactory.createLineString(coords);
        }
        return null;
    }

    /**
     * Create JTS Polygon from coordinates
     */
    private Polygon createPolygon(JsonNode coordinates) {
        if (coordinates.isArray() && coordinates.size() > 0) {
            // Exterior ring
            JsonNode exteriorRing = coordinates.get(0);
            LinearRing shell = createLinearRing(exteriorRing);
            if (shell == null)
                return null;

            // Interior rings (holes)
            LinearRing[] holes = new LinearRing[coordinates.size() - 1];
            for (int i = 1; i < coordinates.size(); i++) {
                holes[i - 1] = createLinearRing(coordinates.get(i));
            }

            return geometryFactory.createPolygon(shell, holes);
        }
        return null;
    }

    /**
     * Create JTS LinearRing from coordinates
     */
    private LinearRing createLinearRing(JsonNode coordinates) {
        if (coordinates.isArray()) {
            Coordinate[] coords = new Coordinate[coordinates.size()];
            for (int i = 0; i < coordinates.size(); i++) {
                JsonNode coord = coordinates.get(i);
                if (coord.isArray() && coord.size() >= 2) {
                    coords[i] = new Coordinate(coord.get(0).asDouble(), coord.get(1).asDouble());
                }
            }
            return geometryFactory.createLinearRing(coords);
        }
        return null;
    }

    /**
     * Create JTS MultiPoint from coordinates
     */
    private MultiPoint createMultiPoint(JsonNode coordinates) {
        if (coordinates.isArray()) {
            org.locationtech.jts.geom.Point[] points = new org.locationtech.jts.geom.Point[coordinates.size()];
            for (int i = 0; i < coordinates.size(); i++) {
                JsonNode coord = coordinates.get(i);
                if (coord.isArray() && coord.size() >= 2) {
                    double x = coord.get(0).asDouble();
                    double y = coord.get(1).asDouble();
                    points[i] = geometryFactory.createPoint(new Coordinate(x, y));
                }
            }
            return geometryFactory.createMultiPoint(points);
        }
        return null;
    }

    /**
     * Create JTS MultiLineString from coordinates
     */
    private MultiLineString createMultiLineString(JsonNode coordinates) {
        if (coordinates.isArray()) {
            LineString[] lineStrings = new LineString[coordinates.size()];
            for (int i = 0; i < coordinates.size(); i++) {
                lineStrings[i] = createLineString(coordinates.get(i));
            }
            return geometryFactory.createMultiLineString(lineStrings);
        }
        return null;
    }

    /**
     * Create JTS MultiPolygon from coordinates
     */
    private MultiPolygon createMultiPolygon(JsonNode coordinates) {
        if (coordinates.isArray()) {
            Polygon[] polygons = new Polygon[coordinates.size()];
            for (int i = 0; i < coordinates.size(); i++) {
                polygons[i] = createPolygon(coordinates.get(i));
            }
            return geometryFactory.createMultiPolygon(polygons);
        }
        return null;
    }

    /**
     * Calculate the number of interpolation points based on distance
     */
    private int calculateInterpolationPoints(Coordinate start, Coordinate end) {
        // Calculate distance in meters
        double distance = DistanceUtils.distHaversineRAD(
                Math.toRadians(start.y), Math.toRadians(start.x),
                Math.toRadians(end.y), Math.toRadians(end.x)) * DistanceUtils.EARTH_MEAN_RADIUS_KM * 1000;

        // Interpolate every 50 meters for better coverage, minimum 2 points, maximum
        // 200 points
        // This ensures we get good coverage even for very long LineStrings
        int points = Math.max(2, Math.min(200, (int) (distance / 50)));

        log.debug("Interpolating {} points for distance of {:.2f} meters between ({:.6f}, {:.6f}) and ({:.6f}, {:.6f})",
                points, distance, start.y, start.x, end.y, end.x);

        return points;
    }

    /**
     * Latitude step for a precision; see {@link GeohashGrid#latStep(int)}.
     */
    private double calculateLatStep(int precision) {
        return GeohashGrid.latStep(precision);
    }

    /**
     * Longitude step for a precision; see {@link GeohashGrid#lonStep(int)}.
     */
    private double calculateLonStep(int precision) {
        return GeohashGrid.lonStep(precision);
    }

    /**
     * Encode latitude and longitude to geohash using ch.hsr.geohash library
     * 
     * @param latitude  Latitude in degrees
     * @param longitude Longitude in degrees
     * @param precision Geohash precision (number of characters)
     * @return Geohash string
     * @throws RuntimeException if geohash encoding fails
     */
    private String encodeGeohash(double latitude, double longitude, int precision) {
        try {
            // Use ch.hsr.geohash library for accurate geohash encoding
            GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, precision);
            return geoHash.toBase32();
        } catch (Exception e) {
            String errorMessage = String.format("Error encoding geohash for lat=%.6f, lon=%.6f, precision=%d: %s",
                    latitude, longitude, precision, e.getMessage());
            log.error(errorMessage, e);

            // Log error to PostgreSQL
            errorLoggingService.logErrorFromRequest(
                    "GEOHASH_ENCODING_ERROR",
                    errorMessage,
                    getStackTraceAsString(e),
                    ErrorLoggingService.ErrorSeverity.HIGH);

            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Extract geohashes from GeoJSON with integrated filtering for better
     * performance
     * 
     * @param geojson   The parsed GeoJSON JsonNode
     * @param precision The geohash precision used for coverage and filtering
     * @return List containing representative geohashes from 3x3 grids
     */
    private List<String> extractGeohashesInternal(JsonNode geojson, int precision,
            Set<String> existingUsedGeohashes) {
        try {
            List<Geometry> geometries = new ArrayList<>();
            Set<String> intersecting = new HashSet<>();
            Set<String> priorityCells = new LinkedHashSet<>();
            GeohashScanBudget scanBudget = new GeohashScanBudget(geofenceProperties.getLimits().getMaxCandidateCells());
            if (geojson.has("type")) {
                String type = geojson.get("type").asText();
                if ("FeatureCollection".equals(type)) {
                    JsonNode features = geojson.get("features");
                    if (features.isArray()) {
                        for (JsonNode feature : features) {
                            collectJtsGeometry(feature.get("geometry"), geometries);
                        }
                    }
                } else if ("Feature".equals(type)) {
                    collectJtsGeometry(geojson.get("geometry"), geometries);
                } else {
                    collectJtsGeometry(geojson, geometries);
                }
            }
            collectIntersectingCells(geometries, intersecting, priorityCells, precision, scanBudget);

            List<String> representatives = selectDeploymentGeohashes(
                    intersecting, priorityCells, existingUsedGeohashes, precision, true);

            if (representatives.isEmpty()) {
                throw new NoAvailableGeohashException("Geohash area saturated for requested deployment area.");
            }
            return representatives;
        } catch (NoAvailableGeohashException | GeofenceTooLargeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in geohash filtering extraction: {}", e.getMessage());
            throw new RuntimeException("Error in geohash filtering extraction", e);
        }
    }

    /**
     * Extract geohashes from GeofenceFeatureCollection with integrated filtering
     * 
     * @param geofenceFeatureCollection The GeofenceFeatureCollection POJO
     * @param precision                 The geohash precision used for coverage and filtering
     * @return List containing representative geohashes from 3x3 grids
     */
    private List<String> extractGeofenceFeatureCollectionInternal(
            GeofenceFeatureCollection geofenceFeatureCollection, int precision, Set<String> existingUsedGeohashes,
            boolean filterRepresentatives) {
        try {
            List<Geometry> geometries = new ArrayList<>();
            Set<String> intersecting = new HashSet<>();
            Set<String> priorityCells = new LinkedHashSet<>();
            GeohashScanBudget scanBudget = new GeohashScanBudget(geofenceProperties.getLimits().getMaxCandidateCells());
            if (geofenceFeatureCollection != null && geofenceFeatureCollection.getFeatures() != null) {
                for (var feature : geofenceFeatureCollection.getFeatures()) {
                    if (feature.getGeometry() != null) {
                        Geometry jtsGeometry = convertCustomGeometryToJTS(feature.getGeometry());
                        if (jtsGeometry != null) {
                            geometries.add(jtsGeometry);
                        }
                    }
                }
            }
            collectIntersectingCells(geometries, intersecting, priorityCells, precision, scanBudget);

            List<String> selected = selectDeploymentGeohashes(
                    intersecting, priorityCells, existingUsedGeohashes, precision,
                    filterRepresentatives);

            if (selected.isEmpty()) {
                throw new NoAvailableGeohashException("No available representative geohashes found (area saturated)");
            }

            return selected;
        } catch (NoAvailableGeohashException | GeofenceTooLargeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in geofence geohash filtering extraction: {}", e.getMessage());
            throw new RuntimeException("Error in geofence geohash filtering extraction", e);
        }
    }

    /**
     * Convert a supported GeoJSON geometry and retain the existing invalid-node
     * warning behavior.
     */
    private void collectJtsGeometry(JsonNode geometry, List<Geometry> sink) {
        if (geometry == null || !geometry.has("type") || !geometry.has("coordinates")) {
            return;
        }
        try {
            Geometry jtsGeometry = convertGeoJSONToJTS(geometry);
            if (jtsGeometry != null) {
                sink.add(jtsGeometry);
            }
        } catch (Exception e) {
            log.warn("Error converting GeoJSON geometry: {}", e.getMessage());
        }
    }

    private void collectIntersectingCells(
            List<Geometry> geometries, Set<String> sink, Set<String> priorityCells,
            int precision, GeohashScanBudget scanBudget) {
        if (geometries.isEmpty()) {
            return;
        }
        Geometry deploymentGeometry = geometries.size() == 1
                ? geometries.get(0)
                : geometryFactory.createGeometryCollection(geometries.toArray(Geometry[]::new));
        collectPriorityCells(deploymentGeometry, precision, priorityCells);
        sink.addAll(collectIntersectingGeohashCells(deploymentGeometry, precision, scanBudget));
    }

    /**
     * Choose deployment cells: keep every intersecting cell until the region
     * exceeds {@code representativeFilterThreshold}, then thin to uniform 3×3
     * representatives.
     */
    private List<String> selectDeploymentGeohashes(
            Set<String> intersectingCells,
            Set<String> priorityCells,
            Set<String> existingUsedGeohashes,
            int precision,
            boolean filterRepresentatives) {
        int filterThreshold = geofenceProperties.getLimits().getRepresentativeFilterThreshold();
        boolean applyFilter = filterRepresentatives
                && intersectingCells != null
                && intersectingCells.size() > Math.max(0, filterThreshold);

        List<String> selected = applyFilter
                ? selectRepresentativesFromIntersectingCells(
                        intersectingCells, priorityCells, existingUsedGeohashes, precision)
                : selectAllAvailableIntersectingCells(intersectingCells, existingUsedGeohashes);

        if (applyFilter) {
            log.debug("Geohash filtering: {} representatives from {} intersecting cells",
                    selected.size(), intersectingCells.size());
        } else if (filterRepresentatives) {
            log.debug("Geohash filtering skipped ({} intersecting cells <= threshold {}): keeping {} available cells",
                    intersectingCells == null ? 0 : intersectingCells.size(),
                    filterThreshold, selected.size());
        } else {
            log.debug("Geohash extraction without representative filtering: {} cells from {} intersecting cells",
                    selected.size(), intersectingCells == null ? 0 : intersectingCells.size());
        }
        return selected;
    }

    /**
     * Select representatives from all cells that intersect the deployment geometry:
     * polygon vertices, one per 3×3 block, a coverage fill for remaining holes,
     * and an optional small-region supplement. Vertex seeding prevents narrow
     * polygon tips from being replaced by a nearby cell whose center happens to
     * be deeper inside the block.
     */
    private List<String> selectRepresentativesFromIntersectingCells(
            Set<String> intersectingCells,
            Set<String> priorityCells,
            Set<String> existingUsedGeohashes,
            int precision) {
        if (intersectingCells == null || intersectingCells.isEmpty()) {
            return List.of();
        }

        Set<String> existingUsed = existingUsedGeohashes == null
                ? Set.of()
                : existingUsedGeohashes;
        double latStep = calculateLatStep(precision);
        double lonStep = calculateLonStep(precision);
        boolean allowSharing = geofenceProperties.getLimits().isAllowOverlappingGeohashes();
        Map<String, long[]> indicesByCell = cellIndexMap(intersectingCells, latStep, lonStep);

        List<String> selected = new ArrayList<>();
        List<long[]> selectedIndices = new ArrayList<>();
        addPriorityRepresentatives(selected, selectedIndices, priorityCells, intersectingCells,
                existingUsed, allowSharing, latStep, lonStep);
        enforceMaximumGeohashCount(selected);
        selectOnePerBlock(intersectingCells, existingUsed, allowSharing, latStep, lonStep,
                PRIMARY_BLOCK_SIZE, selected, selectedIndices, indicesByCell);
        enforceMaximumGeohashCount(selected);
        fillCoverageGaps(selected, selectedIndices, intersectingCells, existingUsed, allowSharing,
                latStep, lonStep, indicesByCell);

        int minGeohashes = geofenceProperties.getLimits().getMinGeohashes();
        if (selected.size() < minGeohashes) {
            log.debug("Below minimum geohashes ({} < {}), running supplement pass",
                    selected.size(), minGeohashes);
            supplementToMinimum(
                    selected, intersectingCells, existingUsed, allowSharing, latStep, lonStep, minGeohashes);
        }

        enforceMaximumGeohashCount(selected);
        return selected;
    }

    /**
     * Return every intersecting cell that is still available, without 3×3
     * representative thinning or min-geohash supplement. Collision avoidance and
     * {@code maxGeohashes} still apply.
     */
    private List<String> selectAllAvailableIntersectingCells(
            Set<String> intersectingCells,
            Set<String> existingUsedGeohashes) {
        if (intersectingCells == null || intersectingCells.isEmpty()) {
            return List.of();
        }

        Set<String> existingUsed = existingUsedGeohashes == null
                ? Set.of()
                : existingUsedGeohashes;
        boolean allowSharing = geofenceProperties.getLimits().isAllowOverlappingGeohashes();

        List<String> selected = new ArrayList<>();
        for (String cell : intersectingCells) {
            if (isCellAvailable(cell, existingUsed, allowSharing)) {
                selected.add(cell);
                enforceMaximumGeohashCount(selected);
            }
        }

        enforceMaximumGeohashCount(selected);
        return selected;
    }

    /**
     * Enumerate every level-{@code precision} geohash cell whose bounding box
     * intersects {@code geometry}. JTS {@code intersects} semantics are deliberate:
     * zero-area edge or corner contact counts as coverage, while cells wholly inside
     * a polygon hole do not intersect the deployment geometry.
     */
    private Set<String> collectIntersectingGeohashCells(
            Geometry geometry, int precision, GeohashScanBudget scanBudget) {
        Set<String> cells = new HashSet<>();
        if (geometry == null || geometry.isEmpty()) {
            return cells;
        }

        List<GeometryScan> scans = new ArrayList<>();
        long candidateCells = 0;
        for (Geometry component : decomposeGeometry(geometry)) {
            BoundingBox bbox = toGeohashBoundingBox(component.getEnvelopeInternal(), precision);
            if (bbox == null) {
                log.warn("Geometry component envelope is invalid; falling back to centroid cell");
                cells.addAll(cellsFromGeometryCentroid(component, precision));
                continue;
            }

            // Reserve every component synchronously before starting workers. This keeps
            // oversized-region rejection deterministic and prevents partial expensive work.
            candidateCells = scanBudget.reserve(bbox, precision);
            scans.add(new GeometryScan(component, bbox));
        }

        if (shouldScanInParallel(scans, candidateCells)) {
            cells.addAll(scanGeometryComponentsInParallel(scans, precision));
        } else {
            for (GeometryScan scan : scans) {
                cells.addAll(scanGeometryComponent(scan, precision));
            }
        }

        if (cells.isEmpty()) {
            cells.addAll(cellsFromGeometryCentroid(geometry, precision));
        }
        return cells;
    }

    /**
     * Split collection geometries into atomic components. Polygon holes remain part
     * of their containing polygon, while disconnected MultiPolygon members receive
     * independent envelopes. This makes a MultiPolygon use the same scan budget as
     * an equivalent FeatureCollection and avoids charging for empty space between
     * distant regions.
     */
    private List<Geometry> decomposeGeometry(Geometry geometry) {
        List<Geometry> components = new ArrayList<>();
        Deque<Geometry> pending = new ArrayDeque<>();
        pending.push(geometry);
        while (!pending.isEmpty()) {
            Geometry current = pending.pop();
            if (current == null || current.isEmpty()) {
                continue;
            }
            if (current instanceof GeometryCollection collection) {
                for (int i = collection.getNumGeometries() - 1; i >= 0; i--) {
                    pending.push(collection.getGeometryN(i));
                }
            } else {
                components.add(current);
            }
        }
        return components;
    }

    private boolean shouldScanInParallel(List<GeometryScan> scans, long candidateCells) {
        return polygonAnalysisExecutor != null
                && scans.size() > 1
                && candidateCells >= PARALLEL_SCAN_MIN_CANDIDATE_CELLS;
    }

    /**
     * Run at most {@code polygonAnalysisParallelism} tasks, irrespective of how
     * many polygon components an adversarial input contains. Each task owns its
     * result set; the calling thread performs the final merge.
     */
    private Set<String> scanGeometryComponentsInParallel(List<GeometryScan> scans, int precision) {
        int workerCount = Math.min(polygonAnalysisParallelism, scans.size());
        AtomicInteger nextScan = new AtomicInteger();
        List<Callable<Set<String>>> workers = new ArrayList<>(workerCount);
        for (int worker = 0; worker < workerCount; worker++) {
            workers.add(() -> {
                Set<String> workerCells = new HashSet<>();
                int index;
                while ((index = nextScan.getAndIncrement()) < scans.size()) {
                    workerCells.addAll(scanGeometryComponent(scans.get(index), precision));
                }
                return workerCells;
            });
        }

        Set<String> cells = new HashSet<>();
        try {
            for (Future<Set<String>> future : polygonAnalysisExecutor.invokeAll(workers)) {
                cells.addAll(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calculating deployment geohashes", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to calculate deployment geohashes", cause);
        }
        return cells;
    }

    private Set<String> scanGeometryComponent(GeometryScan scan, int precision) {
        Set<String> cells = new HashSet<>();
        Geometry geometry = scan.geometry();
        try {

            // Prepare once so the spatial index is built before the per-cell loop.
            PreparedGeometry prepared = PreparedGeometryFactory.prepare(geometry);

            // Enumerate exactly the row/column range reserved by the budget.
            // Walking geohash ordinals can traverse enormous gaps outside a
            // small box, particularly across the equator or prime meridian.
            GeohashScanBudget.CellRange range = GeohashScanBudget.cellRange(scan.boundingBox(), precision);
            for (long row = range.southRow(); row <= range.northRow(); row++) {
                double latitude = (row + 0.5) * range.latitudeStep() - 90.0;
                for (long column = range.westColumn(); column <= range.eastColumn(); column++) {
                    double longitude = (column + 0.5) * range.longitudeStep() - 180.0;
                    GeoHash cell = GeoHash.withCharacterPrecision(latitude, longitude, precision);
                    if (geometryIntersectsGeohashCell(prepared, cell)) {
                        cells.add(cell.toBase32());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("Failed to iterate geohash cells for geometry component ({}), using centroid fallback",
                    e.getMessage());
            return cellsFromGeometryCentroid(geometry, precision);
        }
        if (cells.isEmpty()) {
            cells.addAll(cellsFromGeometryCentroid(geometry, precision));
        }
        return cells;
    }

    private record GeometryScan(Geometry geometry, BoundingBox boundingBox) {
    }

    /**
     * Build a geohash {@link BoundingBox} from a JTS envelope, normalizing
     * inverted/null bounds and including cells that touch grid-aligned minima.
     */
    private BoundingBox toGeohashBoundingBox(Envelope envelope, int precision) {
        if (envelope == null || envelope.isNull()) {
            return null;
        }

        double south = envelope.getMinY();
        double north = envelope.getMaxY();
        double west = envelope.getMinX();
        double east = envelope.getMaxX();

        if (Double.isNaN(south) || Double.isNaN(north) || Double.isNaN(west) || Double.isNaN(east)) {
            return null;
        }

        // JTS may return inverted min/max for some invalid envelopes
        if (south > north) {
            double tmp = south;
            south = north;
            north = tmp;
        }
        if (west > east) {
            double tmp = west;
            west = east;
            east = tmp;
        }

        double latPad = calculateLatStep(precision) / 2.0;
        double lonPad = calculateLonStep(precision) / 2.0;

        // Point or line with zero thickness — pad so at least one cell is scanned
        if (south == north) {
            south -= latPad;
            north += latPad;
        } else {
            south = includeTouchingLowerCell(south, latPad * 2.0, 90.0);
        }
        if (west == east) {
            west -= lonPad;
            east += lonPad;
        } else {
            west = includeTouchingLowerCell(west, lonPad * 2.0, 180.0);
        }

        south = Math.max(-90.0, south);
        north = Math.min(90.0, north);
        west = Math.max(-180.0, west);
        east = Math.min(180.0, east);

        if (south > north || west > east) {
            return null;
        }

        return new BoundingBox(south, north, west, east);
    }

    private static double includeTouchingLowerCell(double coordinate, double step, double origin) {
        double index = (coordinate + origin) / step;
        if (coordinate > -origin && coordinate < origin && index == Math.rint(index)) {
            // Use the neighboring cell's center. A one-ULP decrement can round
            // back onto the boundary when the geohash encoder adds its origin.
            return coordinate - step / 2.0;
        }
        return coordinate;
    }

    private Set<String> cellsFromGeometryCentroid(Geometry geometry, int precision) {
        Set<String> cells = new HashSet<>();
        org.locationtech.jts.geom.Point centroid = geometry.getCentroid();
        if (centroid == null || Double.isNaN(centroid.getY()) || Double.isNaN(centroid.getX())) {
            return cells;
        }
        cells.add(GeoHash.withCharacterPrecision(centroid.getY(), centroid.getX(), precision).toBase32());
        return cells;
    }

    private void collectPriorityCells(Geometry geometry, int precision, Set<String> priorityCells) {
        if (geometry == null || priorityCells == null) {
            return;
        }
        for (Coordinate coordinate : geometry.getCoordinates()) {
            if (coordinate == null
                    || !Double.isFinite(coordinate.x)
                    || !Double.isFinite(coordinate.y)
                    || coordinate.x < -180.0
                    || coordinate.x > 180.0
                    || coordinate.y < -90.0
                    || coordinate.y > 90.0) {
                continue;
            }
            priorityCells.add(GeoHash.withCharacterPrecision(
                    coordinate.y, coordinate.x, precision).toBase32());
        }
    }

    private boolean geometryIntersectsGeohashCell(PreparedGeometry prepared, GeoHash cell) {
        BoundingBox bb = cell.getBoundingBox();
        org.locationtech.jts.geom.Polygon cellPolygon = geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(bb.getWestLongitude(), bb.getSouthLatitude()),
                new Coordinate(bb.getEastLongitude(), bb.getSouthLatitude()),
                new Coordinate(bb.getEastLongitude(), bb.getNorthLatitude()),
                new Coordinate(bb.getWestLongitude(), bb.getNorthLatitude()),
                new Coordinate(bb.getWestLongitude(), bb.getSouthLatitude())
        });
        return prepared.intersects(cellPolygon);
    }

    private void addPriorityRepresentatives(
            List<String> selected,
            List<long[]> selectedIndices,
            Set<String> priorityCells,
            Set<String> intersectingCells,
            Set<String> existingUsed,
            boolean allowSharing,
            double latStep,
            double lonStep) {
        if (priorityCells == null) {
            return;
        }
        for (String cell : priorityCells) {
            if (selected.contains(cell)
                    || !intersectingCells.contains(cell)
                    || !isCellAvailable(cell, existingUsed, allowSharing)) {
                continue;
            }
            long[] indices = cellIndices(GeoHash.fromGeohashString(cell), latStep, lonStep);
            if (isSeparatedFromAll(indices, selectedIndices, SUPPLEMENT_MIN_CELL_SEPARATION)) {
                selected.add(cell);
                selectedIndices.add(indices);
                enforceMaximumGeohashCount(selected);
            }
        }
    }

    /**
     * Pick one representative per {@code blockSize}×{@code blockSize} tile of the
     * requested-precision cell grid, preferring the uncovered cell closest to each
     * tile centre.
     *
     * <p>Blocks are processed nearest-centroid-first so that central
     * representatives are placed before edge blocks. A block is skipped only when
     * every intersecting cell in it is already inside an existing representative's
     * 3×3 coverage zone. Uncovered blocks still receive a representative even when
     * that cell is only {@code COVERAGE_RADIUS + 1} away from a prior pick.
     */
    private void selectOnePerBlock(
            Set<String> intersectingCells,
            Set<String> existingUsed,
            boolean allowSharing,
            double latStep,
            double lonStep,
            int blockSize,
            List<String> selected,
            List<long[]> selectedIndices,
            Map<String, long[]> indicesByCell) {
        Map<String, List<String>> blocks = groupCellsByBlock(intersectingCells, latStep, lonStep, blockSize);

        // Process blocks nearest to the overall centroid first so that central
        // representatives are placed before edge blocks compete for nearby cells.
        double[] centroid = centroidOfCells(intersectingCells);
        List<Map.Entry<String, List<String>>> sortedBlocks = new ArrayList<>(blocks.entrySet());
        sortedBlocks.sort(Comparator.comparingDouble(e -> {
            long[] bi = parseBlockKey(e.getKey());
            double bLat = blockCenterCoordinate(bi[0], blockSize, latStep, 90.0);
            double bLon = blockCenterCoordinate(bi[1], blockSize, lonStep, 180.0);
            double dLat = bLat - centroid[0];
            double dLon = bLon - centroid[1];
            return dLat * dLat + dLon * dLon;
        }));

        Set<String> selectedSet = new HashSet<>(selected);
        for (Map.Entry<String, List<String>> entry : sortedBlocks) {
            if (everyIntersectingCellIsCovered(entry.getValue(), selectedIndices, indicesByCell)) {
                continue;
            }

            long[] blockIndices = parseBlockKey(entry.getKey());
            double blockCenterLat = blockCenterCoordinate(blockIndices[0], blockSize, latStep, 90.0);
            double blockCenterLon = blockCenterCoordinate(blockIndices[1], blockSize, lonStep, 180.0);

            entry.getValue().stream()
                    .filter(cell -> !selectedSet.contains(cell))
                    .filter(cell -> isCellAvailable(cell, existingUsed, allowSharing))
                    .filter(cell -> !isCellCovered(indicesByCell.get(cell), selectedIndices))
                    .min(Comparator.comparingDouble(cell -> distanceToPoint(cell, blockCenterLat, blockCenterLon)))
                    .ifPresent(cell -> {
                        selected.add(cell);
                        selectedSet.add(cell);
                        selectedIndices.add(indicesByCell.get(cell));
                        enforceMaximumGeohashCount(selected);
                    });
        }
    }

    /**
     * Greedy set cover for any intersecting cell still outside every selected 3×3.
     * Prefers nonadjacent candidates, then the most remaining holes and the
     * shortest distance to the block centre. Already-covered candidates remain
     * eligible when occupied cells prevent a nonadjacent selection.
     */
    private void fillCoverageGaps(
            List<String> selected,
            List<long[]> selectedIndices,
            Set<String> intersectingCells,
            Set<String> existingUsed,
            boolean allowSharing,
            double latStep,
            double lonStep,
            Map<String, long[]> indicesByCell) {
        Set<String> selectedSet = new HashSet<>(selected);
        Map<CellIndex, String> cellsByIndex = new HashMap<>();
        for (Map.Entry<String, long[]> entry : indicesByCell.entrySet()) {
            long[] index = entry.getValue();
            cellsByIndex.put(new CellIndex(index[0], index[1]), entry.getKey());
        }

        // Track coverage incrementally. Re-scanning all intersecting cells against
        // every selected representative on each greedy iteration makes fragmented
        // regions expensive.
        Set<String> coveredCells = new HashSet<>();
        for (long[] selectedIndex : selectedIndices) {
            addNeighboringCells(selectedIndex, cellsByIndex, coveredCells);
        }
        Set<String> uncovered = new HashSet<>(intersectingCells);
        uncovered.removeAll(coveredCells);

        while (true) {
            if (uncovered.isEmpty()) {
                return;
            }

            // Every candidate that covers a hole must be in its 3×3 neighborhood.
            // Count those candidates from a grid index instead of comparing every
            // candidate with every uncovered cell.
            Map<String, Integer> coveredCounts = new HashMap<>();
            for (String hole : uncovered) {
                long[] holeIndex = indicesByCell.get(hole);
                for (long latitudeOffset = -COVERAGE_RADIUS; latitudeOffset <= COVERAGE_RADIUS; latitudeOffset++) {
                    for (long longitudeOffset = -COVERAGE_RADIUS; longitudeOffset <= COVERAGE_RADIUS; longitudeOffset++) {
                        String candidate = cellsByIndex.get(new CellIndex(
                                holeIndex[0] + latitudeOffset, holeIndex[1] + longitudeOffset));
                        if (candidate != null) {
                            coveredCounts.merge(candidate, 1, Integer::sum);
                        }
                    }
                }
            }

            // Only cells that cover at least one hole are eligible, and every key of
            // coveredCounts is an intersecting cell. Ties break on geohash order so the
            // choice does not depend on HashMap iteration order.
            String best = null;
            int bestCovered = 0;
            double bestDistance = Double.POSITIVE_INFINITY;
            boolean bestIsCovered = true;
            for (Map.Entry<String, Integer> entry : coveredCounts.entrySet()) {
                String candidate = entry.getKey();
                int coveredCount = entry.getValue();
                if (selectedSet.contains(candidate)
                        || !isCellAvailable(candidate, existingUsed, allowSharing)) {
                    continue;
                }
                long[] candidateIdx = indicesByCell.get(candidate);
                double blockCenterLat = blockCenterCoordinate(
                        Math.floorDiv(candidateIdx[0], PRIMARY_BLOCK_SIZE), PRIMARY_BLOCK_SIZE, latStep, 90.0);
                double blockCenterLon = blockCenterCoordinate(
                        Math.floorDiv(candidateIdx[1], PRIMARY_BLOCK_SIZE), PRIMARY_BLOCK_SIZE, lonStep, 180.0);
                double distance = distanceToPoint(candidate, blockCenterLat, blockCenterLon);
                boolean candidateIsCovered = coveredCells.contains(candidate);
                if (best == null || (bestIsCovered && !candidateIsCovered)
                        || (bestIsCovered == candidateIsCovered
                            && (coveredCount > bestCovered
                                || (coveredCount == bestCovered && distance < bestDistance)
                                || (coveredCount == bestCovered && distance == bestDistance
                                    && candidate.compareTo(best) < 0)))) {
                    best = candidate;
                    bestCovered = coveredCount;
                    bestDistance = distance;
                    bestIsCovered = candidateIsCovered;
                }
            }
            if (best == null) {
                throw new NoAvailableGeohashException(
                        "Cannot cover " + uncovered.size() + " intersecting cells with available geohashes");
            }
            selected.add(best);
            selectedSet.add(best);
            long[] selectedIndex = indicesByCell.get(best);
            selectedIndices.add(selectedIndex);
            enforceMaximumGeohashCount(selected);
            addNeighboringCells(selectedIndex, cellsByIndex, coveredCells);
            uncovered.removeAll(coveredCells);
        }
    }

    /**
     * Greedy supplement for small regions: add cells from finer 3×3 tiles until
     * {@code minGeohashes} is reached, keeping Chebyshev separation from all
     * already-selected cells.
     */
    private void supplementToMinimum(
            List<String> selected,
            Set<String> intersectingCells,
            Set<String> existingUsed,
            boolean allowSharing,
            double latStep,
            double lonStep,
            int minGeohashes) {
        List<long[]> selectedIndices = new ArrayList<>();
        for (String cell : selected) {
            selectedIndices.add(cellIndices(GeoHash.fromGeohashString(cell), latStep, lonStep));
        }

        Set<String> selectedSet = new HashSet<>(selected);
        double[] centroid = centroidOfCells(intersectingCells);
        List<String> candidates = intersectingCells.stream()
                .filter(cell -> !selectedSet.contains(cell))
                .filter(cell -> isCellAvailable(cell, existingUsed, allowSharing))
                .sorted(Comparator.comparingDouble(cell -> distanceToPoint(cell, centroid[0], centroid[1])))
                .toList();

        for (String cell : candidates) {
            if (selected.size() >= minGeohashes) {
                break;
            }
            long[] idx = cellIndices(GeoHash.fromGeohashString(cell), latStep, lonStep);
            if (isSeparatedFromAll(idx, selectedIndices, SUPPLEMENT_MIN_CELL_SEPARATION)) {
                selected.add(cell);
                selectedIndices.add(idx);
                enforceMaximumGeohashCount(selected);
            }
        }
    }

    private void enforceMaximumGeohashCount(List<String> selected) {
        int maxGeohashes = geofenceProperties.getLimits().getMaxGeohashes();
        if (selected.size() > maxGeohashes) {
            throw new GeofenceTooLargeException(
                    "Deployment region is too large: selected geohash count " + selected.size()
                            + " exceeds the configured maximum of " + maxGeohashes
                            + " (geofence.limits.max-geohashes)");
        }
    }

    private void addNeighboringCells(
            long[] center, Map<CellIndex, String> cellsByIndex, Set<String> destination) {
        for (long latitudeOffset = -COVERAGE_RADIUS; latitudeOffset <= COVERAGE_RADIUS; latitudeOffset++) {
            for (long longitudeOffset = -COVERAGE_RADIUS; longitudeOffset <= COVERAGE_RADIUS; longitudeOffset++) {
                String cell = cellsByIndex.get(new CellIndex(
                        center[0] + latitudeOffset, center[1] + longitudeOffset));
                if (cell != null) {
                    destination.add(cell);
                }
            }
        }
    }

    private record CellIndex(long latitude, long longitude) {
    }

    private Map<String, List<String>> groupCellsByBlock(
            Set<String> cells, double latStep, double lonStep, int blockSize) {
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        for (String cell : cells) {
            long[] idx = cellIndices(GeoHash.fromGeohashString(cell), latStep, lonStep);
            String key = blockKey(
                    Math.floorDiv(idx[0], blockSize),
                    Math.floorDiv(idx[1], blockSize));
            blocks.computeIfAbsent(key, k -> new ArrayList<>()).add(cell);
        }
        return blocks;
    }

    private long[] cellIndices(GeoHash cell, double latStep, double lonStep) {
        BoundingBox bb = cell.getBoundingBox();
        long latIdx = (long) Math.floor((bb.getSouthLatitude() + 90.0) / latStep);
        long lonIdx = (long) Math.floor((bb.getWestLongitude() + 180.0) / lonStep);
        return new long[] { latIdx, lonIdx };
    }

    private String blockKey(long blockLat, long blockLon) {
        return blockLat + ":" + blockLon;
    }

    private long[] parseBlockKey(String key) {
        String[] parts = key.split(":");
        return new long[] { Long.parseLong(parts[0]), Long.parseLong(parts[1]) };
    }

    private double blockCenterCoordinate(long blockIndex, int blockSize, double step, double originOffset) {
        return (blockIndex * blockSize + (blockSize / 2.0)) * step - originOffset;
    }

    private double distanceToPoint(String geohash, double lat, double lon) {
        GeoHash cell = GeoHash.fromGeohashString(geohash);
        WGS84Point center = cell.getBoundingBoxCenter();
        double dLat = center.getLatitude() - lat;
        double dLon = center.getLongitude() - lon;
        return dLat * dLat + dLon * dLon;
    }

    private double[] centroidOfCells(Set<String> cells) {
        double sumLat = 0;
        double sumLon = 0;
        int count = 0;
        for (String cell : cells) {
            WGS84Point center = GeoHash.fromGeohashString(cell).getBoundingBoxCenter();
            sumLat += center.getLatitude();
            sumLon += center.getLongitude();
            count++;
        }
        if (count == 0) {
            return new double[] { 0, 0 };
        }
        return new double[] { sumLat / count, sumLon / count };
    }

    private boolean isCellAvailable(String cell, Set<String> existingUsed, boolean allowSharing) {
        return allowSharing || !existingUsed.contains(cell);
    }

    private Map<String, long[]> cellIndexMap(Set<String> cells, double latStep, double lonStep) {
        Map<String, long[]> indicesByCell = new HashMap<>();
        for (String cell : cells) {
            indicesByCell.put(cell, cellIndices(GeoHash.fromGeohashString(cell), latStep, lonStep));
        }
        return indicesByCell;
    }

    /**
     * True when every listed cell sits inside some selected representative's 3×3
     * neighborhood (Chebyshev {@code <= COVERAGE_RADIUS}).
     */
    private boolean everyIntersectingCellIsCovered(
            Iterable<String> cells, List<long[]> selectedIndices, Map<String, long[]> indicesByCell) {
        for (String cell : cells) {
            if (!isCellCovered(indicesByCell.get(cell), selectedIndices)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCellCovered(long[] cellIdx, List<long[]> selectedIndices) {
        if (cellIdx == null) {
            return false;
        }
        for (long[] selected : selectedIndices) {
            if (chebyshevDistance(cellIdx, selected) <= COVERAGE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private long chebyshevDistance(long[] first, long[] second) {
        return Math.max(Math.abs(first[0] - second[0]), Math.abs(first[1] - second[1]));
    }

    private boolean isSeparatedFromAll(long[] candidateIdx, List<long[]> selectedIndices, int minSeparation) {
        for (long[] selected : selectedIndices) {
            if (chebyshevDistance(candidateIdx, selected) < minSeparation) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convert custom geometry to JTS Geometry
     */
    private Geometry convertCustomGeometryToJTS(
            usdot.v2x.app.api.models.etx.configuration.geometry.Geometry geometry) {
        if (geometry instanceof usdot.v2x.app.api.models.etx.configuration.geometry.Polygon) {
            return createJtsPolygonFromCustomPolygon(
                    (usdot.v2x.app.api.models.etx.configuration.geometry.Polygon) geometry);
        } else if (geometry instanceof usdot.v2x.app.api.models.etx.configuration.geometry.LineString) {
            return createJtsLineStringFromCustomLineString(
                    (usdot.v2x.app.api.models.etx.configuration.geometry.LineString) geometry);
        } else if (geometry instanceof usdot.v2x.app.api.models.etx.configuration.geometry.MultiPolygon) {
            return GeometryConverter.toJtsGeometry(geometry);
        }
        // Add other geometry types as needed
        return null;
    }

    /**
     * Create JTS LineString from custom LineString POJO
     */
    private LineString createJtsLineStringFromCustomLineString(
            usdot.v2x.app.api.models.etx.configuration.geometry.LineString lineString) {
        if (lineString.getCoordinates() != null && !lineString.getCoordinates().isEmpty()) {
            Coordinate[] coords = new Coordinate[lineString.getCoordinates().size()];
            for (int i = 0; i < lineString.getCoordinates().size(); i++) {
                List<Double> coord = lineString.getCoordinates().get(i);
                if (coord.size() >= 2) {
                    coords[i] = new Coordinate(coord.get(0), coord.get(1));
                }
            }
            return geometryFactory.createLineString(coords);
        }
        return null;
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception exception) {
        if (exception == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Fetch currently active geohashes from the database for collision avoidance.
     */
    private Set<String> fetchActiveGeohashes() {
        try {
            if (jdbcTemplate == null) {
                return java.util.Collections.emptySet();
            }
            List<String> rows = jdbcTemplate.queryForList("SELECT geohash FROM active_geofence_geohashes",
                    String.class);
            return new HashSet<>(rows);
        } catch (Exception e) {
            log.warn("Failed to fetch active geohashes from DB: {}", e.getMessage());
            return java.util.Collections.emptySet();
        }
    }

    /**
     * Fetch active geohashes excluding those of a specific geofenceId (if
     * provided).
     */
    private Set<String> fetchActiveGeohashesExcluding(String geofenceIdToExclude) {
        Set<String> active = fetchActiveGeohashes();
        try {
            if (jdbcTemplate == null) {
                return active;
            }
            if (geofenceIdToExclude == null || geofenceIdToExclude.isBlank()) {
                return active;
            }
            List<String> own = jdbcTemplate.queryForList(
                    "SELECT geohash FROM get_geofence_geohashes(?)",
                    String.class,
                    geofenceIdToExclude);
            if (!own.isEmpty()) {
                active.removeAll(own);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch or exclude geofence {} geohashes: {}", geofenceIdToExclude, e.getMessage());
        }
        return active;
    }

    /**
     * Get counts of active deployments per geohash for the provided list, excluding
     * a given geofenceId.
     */
    public Map<String, Integer> getActiveOverlapCounts(List<String> geohashes, String geofenceIdToExclude) {
        Map<String, Integer> counts = new HashMap<>();
        if (geohashes == null || geohashes.isEmpty() || jdbcTemplate == null) {
            return counts;
        }
        try {
            // Query counts for the provided geohashes
            String sql = "SELECT gg.geohash, COUNT(*) AS cnt " +
                    "FROM geofence_geohashes gg " +
                    "JOIN geofence_deployments gd ON gd.id = gg.geofence_deployment_id " +
                    "WHERE gg.geohash = ANY (?) " +
                    "AND gd.is_active = TRUE " +
                    "AND (gd.expires_at IS NULL OR gd.expires_at > CURRENT_TIMESTAMP) " +
                    (geofenceIdToExclude != null && !geofenceIdToExclude.isBlank() ? "AND gd.geofence_id <> ? " : "") +
                    "GROUP BY gg.geohash";

            Object[] params;
            int[] types;
            if (geofenceIdToExclude != null && !geofenceIdToExclude.isBlank()) {
                params = new Object[] { geohashes.toArray(new String[0]), geofenceIdToExclude };
                types = new int[] { java.sql.Types.ARRAY, java.sql.Types.VARCHAR };
            } else {
                params = new Object[] { geohashes.toArray(new String[0]) };
                types = new int[] { java.sql.Types.ARRAY };
            }

            jdbcTemplate.query(sql, params, rs -> {
                String gh = rs.getString("geohash");
                int cnt = rs.getInt("cnt");
                counts.put(gh, cnt);
            });
        } catch (Exception e) {
            log.warn("Failed to fetch overlap counts: {}", e.getMessage());
        }
        return counts;
    }

    /**
     * Return true if any provided geohash exceeds the threshold of active overlaps.
     */
    public boolean hasExcessiveOverlap(List<String> geohashes, String geofenceIdToExclude, int threshold) {
        if (geohashes == null || geohashes.isEmpty()) {
            return false;
        }
        Map<String, Integer> counts = getActiveOverlapCounts(geohashes, geofenceIdToExclude);
        for (String gh : geohashes) {
            Integer c = counts.get(gh);
            if (c != null && c >= threshold) {
                return true;
            }
        }
        return false;
    }
}
