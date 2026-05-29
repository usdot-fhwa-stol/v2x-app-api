package usdot.v2x.app.api.utils;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import ch.hsr.geohash.util.BoundingBoxGeoHashIterator;
import ch.hsr.geohash.util.TwoGeoHashBoundingBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.services.ErrorLoggingService;
import lombok.extern.slf4j.Slf4j;
import usdot.v2x.app.api.exceptions.NoAvailableGeohashException;
import org.locationtech.jts.geom.*;
import org.locationtech.spatial4j.distance.DistanceUtils;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for geohash operations and geospatial data processing.
 */
@Component
@Slf4j
public class GeohashUtils {

    /** Level-7 geohash precision used for deployment representatives. */
    private static final int GRID_PRECISION = 7;
    /**
     * One representative per this many cells in each direction (~9×9 coverage
     * zone).
     */
    private static final int PRIMARY_BLOCK_SIZE = 9;
    /**
     * Minimum cell-index separation when topping up small regions to minGeohashes.
     */
    private static final int SUPPLEMENT_MIN_CELL_SEPARATION = 3;

    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory;
    private final ErrorLoggingService errorLoggingService;
    private final GeofenceProperties geofenceProperties;
    private final JdbcTemplate jdbcTemplate;

    // Cache for step size calculations to avoid repeated calculations
    private final Map<Integer, Double> latStepCache = new HashMap<>();
    private final Map<Integer, Double> lonStepCache = new HashMap<>();

    public GeohashUtils(ObjectMapper objectMapper, ErrorLoggingService errorLoggingService,
            GeofenceProperties geofenceProperties, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.geometryFactory = new GeometryFactory();
        this.errorLoggingService = errorLoggingService;
        this.geofenceProperties = geofenceProperties;
        this.jdbcTemplate = jdbcTemplate;
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
                    existingUsed);
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
        Set<String> existingUsed = fetchActiveGeohashesExcluding(geofenceId);
        return extractGeofenceFeatureCollectionInternal(geofenceFeatureCollection,
                geofenceProperties.getGeohash().getPrecision(), existingUsed);
    }

    /**
     * Extract geohashes from GeofenceFeatureCollection with a set of already-used
     * geohashes to avoid collisions
     */
    public List<String> extractGeohashesFromGeofenceFeatureCollection(
            GeofenceFeatureCollection geofenceFeatureCollection, int precision, Set<String> existingUsedGeohashes) {
        try {
            return extractGeofenceFeatureCollectionInternal(geofenceFeatureCollection, precision,
                    existingUsedGeohashes == null ? java.util.Collections.emptySet() : existingUsedGeohashes);
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
     * Calculate latitude step size for geohash grid
     */
    private double calculateLatStep(int precision) {
        return latStepCache.computeIfAbsent(precision, p -> {
            // More accurate step size based on geohash precision
            // Geohash uses base32 encoding, so each character represents 5 bits
            // For latitude: 90 degrees / 2^(bits/2)
            int bits = p * 5;
            int latBits = bits / 2;
            return 90.0 / Math.pow(2, latBits);
        });
    }

    /**
     * Calculate longitude step size for geohash grid
     */
    private double calculateLonStep(int precision) {
        return lonStepCache.computeIfAbsent(precision, p -> {
            // More accurate step size based on geohash precision
            // For longitude: 180 degrees / 2^(bits/2)
            int bits = p * 5;
            int lonBits = (bits + 1) / 2; // Longitude gets the extra bit for odd precision
            return 180.0 / Math.pow(2, lonBits);
        });
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
     * @param precision The geohash precision (will be overridden to 7 for
     *                  filtering)
     * @return List containing representative geohashes from 3x3 grids
     */
    private List<String> extractGeohashesInternal(JsonNode geojson, int precision,
            Set<String> existingUsedGeohashes) {
        int gridPrecision = 7; // Use level 7 for geohash filtering
        int gridSize = 3;

        // Track geohashes that are already covered by existing 3x3 grids
        Set<String> affectedGeohashes = new HashSet<>();
        List<String> representativeGeohashes = new ArrayList<>();

        // Pre-calculate step sizes for performance
        double latStep = calculateLatStep(gridPrecision);
        double lonStep = calculateLonStep(gridPrecision);

        try {
            Set<String> intersecting = new HashSet<>();
            if (geojson.has("type")) {
                String type = geojson.get("type").asText();
                if ("FeatureCollection".equals(type)) {
                    JsonNode features = geojson.get("features");
                    if (features.isArray()) {
                        for (JsonNode feature : features) {
                            collectIntersectingCellsFromJsonGeometry(feature.get("geometry"), intersecting);
                        }
                    }
                } else if ("Feature".equals(type)) {
                    collectIntersectingCellsFromJsonGeometry(geojson.get("geometry"), intersecting);
                } else {
                    collectIntersectingCellsFromJsonGeometry(geojson, intersecting);
                }
            }

            List<String> representatives = selectRepresentativesFromIntersectingCells(
                    intersecting, existingUsedGeohashes);

            log.debug("Geohash filtering: {} representatives from {} intersecting cells",
                    representatives.size(), intersecting.size());

            if (representatives.isEmpty()) {
                throw new NoAvailableGeohashException("Geohash area saturated for requested deployment area.");
            }
            return representatives;
        } catch (NoAvailableGeohashException e) {
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
     * @param precision                 The geohash precision (will be overridden to
     *                                  7 for filtering)
     * @return List containing representative geohashes from 3x3 grids
     */
    private List<String> extractGeofenceFeatureCollectionInternal(
            GeofenceFeatureCollection geofenceFeatureCollection, int precision, Set<String> existingUsedGeohashes) {
        try {
            Set<String> intersecting = new HashSet<>();
            if (geofenceFeatureCollection != null && geofenceFeatureCollection.getFeatures() != null) {
                for (var feature : geofenceFeatureCollection.getFeatures()) {
                    if (feature.getGeometry() != null) {
                        Geometry jtsGeometry = convertCustomGeometryToJTS(feature.getGeometry());
                        if (jtsGeometry != null) {
                            intersecting.addAll(collectIntersectingGeohashCells(jtsGeometry, GRID_PRECISION));
                        }
                    }
                }
            }

            List<String> representatives = selectRepresentativesFromIntersectingCells(
                    intersecting, existingUsedGeohashes);

            log.debug("Geohash filtering: {} representatives from {} intersecting cells",
                    representatives.size(), intersecting.size());

            if (representatives.isEmpty()) {
                throw new NoAvailableGeohashException("No available representative geohashes found (area saturated)");
            }

            return representatives;
        } catch (NoAvailableGeohashException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in geofence geohash filtering extraction: {}", e.getMessage());
            throw new RuntimeException("Error in geofence geohash filtering extraction", e);
        }
    }

    /**
     * Collect level-7 cells intersecting a GeoJSON geometry node into {@code sink}.
     */
    private void collectIntersectingCellsFromJsonGeometry(JsonNode geometry, Set<String> sink) {
        if (geometry == null || !geometry.has("type") || !geometry.has("coordinates")) {
            return;
        }
        try {
            Geometry jtsGeometry = convertGeoJSONToJTS(geometry);
            if (jtsGeometry != null) {
                sink.addAll(collectIntersectingGeohashCells(jtsGeometry, GRID_PRECISION));
            }
        } catch (Exception e) {
            log.warn("Error collecting geohash cells from GeoJSON geometry: {}", e.getMessage());
        }
    }

    /**
     * Select representatives from all level-7 cells that intersect the deployment
     * geometry: one per 9×9 block, then optional supplement for small regions.
     */
    private List<String> selectRepresentativesFromIntersectingCells(
            Set<String> intersectingCells, Set<String> existingUsedGeohashes) {
        if (intersectingCells == null || intersectingCells.isEmpty()) {
            return List.of();
        }

        Set<String> existingUsed = existingUsedGeohashes == null
                ? Set.of()
                : existingUsedGeohashes;
        double latStep = calculateLatStep(GRID_PRECISION);
        double lonStep = calculateLonStep(GRID_PRECISION);
        boolean allowSharing = geofenceProperties.getLimits().isAllowOverlappingGeohashes();

        List<String> selected = selectOnePerBlock(
                intersectingCells, existingUsed, allowSharing, latStep, lonStep, PRIMARY_BLOCK_SIZE);

        int minGeohashes = geofenceProperties.getLimits().getMinGeohashes();
        if (selected.size() < minGeohashes) {
            log.debug("Below minimum geohashes ({} < {}), running supplement pass",
                    selected.size(), minGeohashes);
            supplementToMinimum(
                    selected, intersectingCells, existingUsed, allowSharing, latStep, lonStep, minGeohashes);
        }

        return selected;
    }

    /**
     * Enumerate every level-{@code precision} geohash cell whose bounding box
     * intersects {@code geometry}.
     */
    private Set<String> collectIntersectingGeohashCells(Geometry geometry, int precision) {
        Set<String> cells = new HashSet<>();
        if (geometry == null || geometry.isEmpty()) {
            return cells;
        }

        try {
            Envelope envelope = geometry.getEnvelopeInternal();
            BoundingBox bbox = toGeohashBoundingBox(envelope, precision);
            if (bbox == null) {
                log.warn("Geometry envelope is invalid or degenerate; falling back to centroid cell");
                return cellsFromGeometryCentroid(geometry, precision);
            }

            TwoGeoHashBoundingBox geoHashBox = TwoGeoHashBoundingBox.withCharacterPrecision(bbox, precision);
            BoundingBoxGeoHashIterator iterator = new BoundingBoxGeoHashIterator(geoHashBox);

            while (iterator.hasNext()) {
                GeoHash cell = iterator.next();
                if (geometryIntersectsGeohashCell(geometry, cell)) {
                    cells.add(cell.toBase32());
                }
            }

            if (cells.isEmpty()) {
                cells.addAll(cellsFromGeometryCentroid(geometry, precision));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Failed to iterate geohash cells for geometry ({}), using centroid fallback",
                    e.getMessage());
            return cellsFromGeometryCentroid(geometry, precision);
        }
        return cells;
    }

    /**
     * Build a geohash {@link BoundingBox} from a JTS envelope, normalizing
     * inverted/null bounds and expanding zero-area envelopes so iteration works.
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
        }
        if (west == east) {
            west -= lonPad;
            east += lonPad;
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

    private Set<String> cellsFromGeometryCentroid(Geometry geometry, int precision) {
        Set<String> cells = new HashSet<>();
        org.locationtech.jts.geom.Point centroid = geometry.getCentroid();
        if (centroid == null || Double.isNaN(centroid.getY()) || Double.isNaN(centroid.getX())) {
            return cells;
        }
        cells.add(GeoHash.withCharacterPrecision(centroid.getY(), centroid.getX(), precision).toBase32());
        return cells;
    }

    private boolean geometryIntersectsGeohashCell(Geometry geometry, GeoHash cell) {
        BoundingBox bb = cell.getBoundingBox();
        org.locationtech.jts.geom.Polygon cellPolygon = geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(bb.getWestLongitude(), bb.getSouthLatitude()),
                new Coordinate(bb.getEastLongitude(), bb.getSouthLatitude()),
                new Coordinate(bb.getEastLongitude(), bb.getNorthLatitude()),
                new Coordinate(bb.getWestLongitude(), bb.getNorthLatitude()),
                new Coordinate(bb.getWestLongitude(), bb.getSouthLatitude())
        });
        return geometry.intersects(cellPolygon);
    }

    /**
     * Pick exactly one representative per {@code blockSize}×{@code blockSize} tile
     * of the level-7 cell grid, preferring the cell closest to each tile centre.
     */
    private List<String> selectOnePerBlock(
            Set<String> intersectingCells,
            Set<String> existingUsed,
            boolean allowSharing,
            double latStep,
            double lonStep,
            int blockSize) {
        Map<String, List<String>> blocks = groupCellsByBlock(intersectingCells, latStep, lonStep, blockSize);
        List<String> selected = new ArrayList<>(blocks.size());

        for (Map.Entry<String, List<String>> entry : blocks.entrySet()) {
            long[] blockIndices = parseBlockKey(entry.getKey());
            double blockCenterLat = blockCenterCoordinate(blockIndices[0], blockSize, latStep, 90.0);
            double blockCenterLon = blockCenterCoordinate(blockIndices[1], blockSize, lonStep, 180.0);

            entry.getValue().stream()
                    .filter(cell -> isCellAvailable(cell, existingUsed, allowSharing))
                    .min(Comparator.comparingDouble(cell -> distanceToPoint(cell, blockCenterLat, blockCenterLon)))
                    .ifPresent(selected::add);
        }

        return selected;
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

        double[] centroid = centroidOfCells(intersectingCells);
        List<String> candidates = intersectingCells.stream()
                .filter(cell -> !selected.contains(cell))
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
            }
        }
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

    private boolean isSeparatedFromAll(long[] candidateIdx, List<long[]> selectedIndices, int minSeparation) {
        for (long[] selected : selectedIndices) {
            long latDelta = Math.abs(candidateIdx[0] - selected[0]);
            long lonDelta = Math.abs(candidateIdx[1] - selected[1]);
            if (Math.max(latDelta, lonDelta) < minSeparation) {
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