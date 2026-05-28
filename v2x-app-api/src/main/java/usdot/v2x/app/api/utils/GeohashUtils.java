package usdot.v2x.app.api.utils;

import ch.hsr.geohash.GeoHash;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for geohash operations and geospatial data processing.
 */
@Component
@Slf4j
public class GeohashUtils {

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
            if (geojson.has("type")) {
                String type = geojson.get("type").asText();

                if ("FeatureCollection".equals(type)) {
                    JsonNode features = geojson.get("features");
                    if (features.isArray()) {
                        for (JsonNode feature : features) {
                            processGeometry(feature.get("geometry"), gridPrecision, gridSize,
                                    latStep, lonStep, affectedGeohashes, representativeGeohashes,
                                    existingUsedGeohashes);
                        }
                    }
                } else if ("Feature".equals(type)) {
                    processGeometry(geojson.get("geometry"), gridPrecision, gridSize,
                            latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
                } else {
                    // Direct geometry
                    processGeometry(geojson, gridPrecision, gridSize,
                            latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
                }
            }

            log.debug("Geohash filtering: generated {} representative geohashes covering {} total geohashes",
                    representativeGeohashes.size(), affectedGeohashes.size());

            if (representativeGeohashes.isEmpty()) {
                throw new NoAvailableGeohashException("Geohash area saturated for requested deployment area.");
            }
            return representativeGeohashes;
        } catch (NoAvailableGeohashException e) {
            // propagate as-is so API can return 409
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
        int gridPrecision = 7; // Use level 7 for geohash filtering
        int gridSize = 3;

        // Track geohashes that are already covered by existing 3x3 grids
        Set<String> affectedGeohashes = new HashSet<>();
        List<String> representativeGeohashes = new ArrayList<>();

        // Pre-calculate step sizes for performance
        double latStep = calculateLatStep(gridPrecision);
        double lonStep = calculateLonStep(gridPrecision);

        try {
            if (geofenceFeatureCollection != null && geofenceFeatureCollection.getFeatures() != null) {
                for (var feature : geofenceFeatureCollection.getFeatures()) {
                    if (feature.getGeometry() != null) {
                        processCustomGeometry(feature.getGeometry(), gridPrecision, gridSize,
                                latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
                    }
                }
            }

            log.debug("Geohash filtering: generated {} representative geohashes covering {} total geohashes",
                    representativeGeohashes.size(), affectedGeohashes.size());

            if (representativeGeohashes.isEmpty()) {
                throw new NoAvailableGeohashException("No available representative geohashes found (area saturated)");
            }

            int minGeohashes = geofenceProperties.getLimits().getMinGeohashes();
            if (representativeGeohashes.size() < minGeohashes) {
                log.debug("Below minimum geohashes ({} < {}), running boundary enforcement pass",
                        representativeGeohashes.size(), minGeohashes);
                enforceMinimumBoundaryGeohashes(geofenceFeatureCollection, gridPrecision, gridSize,
                        latStep, lonStep, affectedGeohashes, representativeGeohashes,
                        existingUsedGeohashes, minGeohashes);
                log.debug("After boundary enforcement: {} representative geohashes",
                        representativeGeohashes.size());
            }

            return representativeGeohashes;
        } catch (NoAvailableGeohashException e) {
            // propagate as-is so API can return 409
            throw e;
        } catch (Exception e) {
            log.error("Error in geofence geohash filtering extraction: {}", e.getMessage());
            throw new RuntimeException("Error in geofence geohash filtering extraction", e);
        }
    }

    /**
     * Process a geometry with integrated filtering
     */
    private void processGeometry(JsonNode geometry, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        if (geometry == null || !geometry.has("type") || !geometry.has("coordinates")) {
            return;
        }

        try {
            // Convert GeoJSON to JTS Geometry
            Geometry jtsGeometry = convertGeoJSONToJTS(geometry);
            if (jtsGeometry != null) {
                processJTSGeometry(jtsGeometry, gridPrecision, gridSize,
                        latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
            }
        } catch (Exception e) {
            log.warn("Error converting geometry for filtering: {}", e.getMessage());
        }
    }

    /**
     * Process a custom geometry with integrated filtering
     */
    private void processCustomGeometry(
            usdot.v2x.app.api.models.etx.configuration.geometry.Geometry geometry,
            int gridPrecision, int gridSize, double latStep, double lonStep,
            Set<String> affectedGeohashes, List<String> representativeGeohashes, Set<String> existingUsedGeohashes) {
        if (geometry == null) {
            return;
        }

        try {
            // Convert custom geometry to JTS Geometry
            Geometry jtsGeometry = convertCustomGeometryToJTS(geometry);
            if (jtsGeometry != null) {
                processJTSGeometry(jtsGeometry, gridPrecision, gridSize,
                        latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
            }
        } catch (Exception e) {
            log.warn("Error converting custom geometry for filtering: {}", e.getMessage());
        }
    }

    /**
     * Process JTS geometry with integrated filtering
     */
    private void processJTSGeometry(Geometry geometry, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        if (geometry == null) {
            return;
        }

        // Handle different geometry types
        if (geometry instanceof org.locationtech.jts.geom.Point) {
            processPoint((org.locationtech.jts.geom.Point) geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        } else if (geometry instanceof LineString) {
            processLineString((LineString) geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        } else if (geometry instanceof Polygon) {
            processPolygon((Polygon) geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        } else if (geometry instanceof MultiPoint) {
            processMultiPoint((MultiPoint) geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        } else if (geometry instanceof MultiLineString) {
            processMultiLineString((MultiLineString) geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        } else if (geometry instanceof MultiPolygon) {
            processMultiPolygon((MultiPolygon) geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        } else if (geometry instanceof GeometryCollection) {
            processGeometryCollection((GeometryCollection) geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        }
    }

    /**
     * Process a point with filtering
     */
    private void processPoint(org.locationtech.jts.geom.Point point, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        double lat = point.getY();
        double lon = point.getX();
        processCoordinate(lat, lon, gridPrecision, gridSize,
                latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
    }

    /**
     * Process a LineString with filtering
     */
    private void processLineString(LineString lineString, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        Coordinate[] coordinates = lineString.getCoordinates();

        // Process each coordinate
        for (Coordinate coord : coordinates) {
            processCoordinate(coord.y, coord.x, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        }

        // Process interpolated points for better coverage
        for (int i = 0; i < coordinates.length - 1; i++) {
            Coordinate start = coordinates[i];
            Coordinate end = coordinates[i + 1];

            int numPoints = calculateInterpolationPoints(start, end);
            for (int j = 1; j < numPoints; j++) {
                double ratio = (double) j / numPoints;
                double lat = start.y + (end.y - start.y) * ratio;
                double lon = start.x + (end.x - start.x) * ratio;
                processCoordinate(lat, lon, gridPrecision, gridSize,
                        latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
            }
        }
    }

    /**
     * Process a polygon with filtering
     */
    private void processPolygon(Polygon polygon, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        // Scan the polygon interior at 4-cell intervals to follow the path centerline.
        // Ring vertices (which fall on the road boundary) are intentionally skipped so
        // that only center-of-road cells are selected.
        processPolygonInterpolation(polygon, gridPrecision, gridSize,
                latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
    }

    /**
     * Process multi-geometry types
     */
    private void processMultiPoint(MultiPoint multiPoint, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        for (int i = 0; i < multiPoint.getNumGeometries(); i++) {
            org.locationtech.jts.geom.Point point = (org.locationtech.jts.geom.Point) multiPoint.getGeometryN(i);
            processPoint(point, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        }
    }

    private void processMultiLineString(MultiLineString multiLineString, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            LineString lineString = (LineString) multiLineString.getGeometryN(i);
            processLineString(lineString, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        }
    }

    private void processMultiPolygon(MultiPolygon multiPolygon, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
            processPolygon(polygon, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        }
    }

    private void processGeometryCollection(GeometryCollection geometryCollection, int gridPrecision,
            int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
            Geometry geometry = geometryCollection.getGeometryN(i);
            processJTSGeometry(geometry, gridPrecision, gridSize,
                    latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
        }
    }

    /**
     * Process a single coordinate with filtering - this is the core filtering logic
     */
    private void processCoordinate(double lat, double lon, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        try {
            // Generate the geohash for this coordinate
            String geohash = encodeGeohash(lat, lon, gridPrecision);

            // Skip if this geohash is already covered by a previous 3x3 grid
            if (affectedGeohashes.contains(geohash)) {
                return;
            }

            // Generate the 3x3 grid around this coordinate
            List<String> gridGeohashes = generate3x3GridOptimized(lat, lon, gridPrecision, gridSize, latStep, lonStep);

            // Check if any of the geohashes in this grid are already covered.
            // When allowOverlappingGeohashes is enabled the overlap check is skipped so
            // that denser coverage is achieved (e.g. path-based TIMs). The center-cell
            // early-exit above still prevents exact duplicate representatives.
            boolean hasOverlap = false;
            if (!geofenceProperties.getLimits().isAllowOverlappingGeohashes()) {
                for (String gridGeohash : gridGeohashes) {
                    if (affectedGeohashes.contains(gridGeohash)) {
                        hasOverlap = true;
                        break;
                    }
                }
            }

            // Only add this representative if there's no overlap with grids in this run
            if (!hasOverlap) {
                // When allowOverlappingGeohashes is true, cross-deployment sharing is
                // permitted, so existingUsedGeohashes is ignored entirely.
                boolean allowSharing = geofenceProperties.getLimits().isAllowOverlappingGeohashes();

                // Choose a representative geohash, preferring center but avoiding already-used
                // ones
                String chosen = null;
                // Center first
                if ((allowSharing || existingUsedGeohashes == null || !existingUsedGeohashes.contains(geohash))
                        && !representativeGeohashes.contains(geohash)) {
                    chosen = geohash;
                } else {
                    // Try neighbors not in existing used set
                    for (String candidate : gridGeohashes) {
                        if (candidate.equals(geohash)) {
                            continue;
                        }
                        if ((allowSharing || existingUsedGeohashes == null || !existingUsedGeohashes.contains(candidate))
                                && !representativeGeohashes.contains(candidate)) {
                            chosen = candidate;
                            break;
                        }
                    }
                }
                // If all 9 cells in this grid are claimed by other active deployments,
                // skip this scan point rather than aborting the entire deployment.
                // Total saturation is reported by the empty-list check after the full scan.
                if (chosen == null) {
                    log.debug("All 3x3 candidates already used at lat={}, lon={} — skipping", lat, lon);
                    return;
                }
                // Mark all geohashes in this grid as affected and record representative
                affectedGeohashes.addAll(gridGeohashes);
                representativeGeohashes.add(chosen);
            }
        } catch (NoAvailableGeohashException e) {
            log.warn("No available representative geohash: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Error processing coordinate ({}, {}) with filtering: {}", lat, lon, e.getMessage());
            throw new NoAvailableGeohashException(e.getMessage(), e);
        }
    }

    /**
     * Generate a 3x3 grid with pre-calculated step sizes for better performance
     */
    private List<String> generate3x3GridOptimized(double latitude, double longitude, int precision, int gridSize,
            double latStep, double lonStep) {
        List<String> geohashes = new ArrayList<>(gridSize * gridSize);

        // Calculate the offset to center the grid around the given point
        int halfGrid = gridSize / 2;
        double startLat = latitude - (halfGrid * latStep);
        double startLon = longitude - (halfGrid * lonStep);

        // Generate the grid
        for (int i = 0; i < gridSize; i++) {
            double currentLat = startLat + (i * latStep);
            currentLat = Math.max(-90.0, Math.min(90.0, currentLat));

            for (int j = 0; j < gridSize; j++) {
                double currentLon = startLon + (j * lonStep);
                currentLon = Math.max(-180.0, Math.min(180.0, currentLon));

                geohashes.add(encodeGeohash(currentLat, currentLon, precision));
            }
        }

        return geohashes;
    }

    /**
     * Process polygon interpolation
     */
    private void processPolygonInterpolation(Polygon polygon, int gridPrecision, int gridSize,
            double latStep, double lonStep, Set<String> affectedGeohashes, List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        try {
            Envelope envelope = polygon.getEnvelopeInternal();

            // Scan at 4-cell intervals so selected geohashes follow the path centerline
            // and are never adjacent to each other (4 > gridSize=3, so 3x3 grids never
            // overlap between consecutive scan points).
            double stepLat = latStep * 4;
            double stepLon = lonStep * 4;

            // Align the scan grid to the polygon's bounding-box center rather than its
            // minimum corner. Starting from minX/minY risks placing the first scan line
            // exactly on the polygon's boundary (contains() = false) while the next line
            // overshoots a narrow tapered section entirely. Centering the grid ensures
            // that every tapered tip — at either end of the path — is within half a step
            // of a scan line, so 4-cell-spaced coverage works for curved corridors too.
            double centerLat = (envelope.getMinY() + envelope.getMaxY()) / 2.0;
            double centerLon = (envelope.getMinX() + envelope.getMaxX()) / 2.0;
            double startLat = centerLat
                    - Math.floor((centerLat - envelope.getMinY()) / stepLat) * stepLat;
            double startLon = centerLon
                    - Math.floor((centerLon - envelope.getMinX()) / stepLon) * stepLon;

            for (double lat = startLat; lat <= envelope.getMaxY(); lat += stepLat) {
                for (double lon = startLon; lon <= envelope.getMaxX(); lon += stepLon) {
                    org.locationtech.jts.geom.Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
                    if (polygon.contains(point)) {
                        processCoordinate(lat, lon, gridPrecision, gridSize,
                                latStep, lonStep, affectedGeohashes, representativeGeohashes, existingUsedGeohashes);
                    }
                }
            }
        } catch (NoAvailableGeohashException e) {
            log.warn("Error in polygon interpolation with filtering: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Error in polygon interpolation with filtering: {}", e.getMessage());
            throw new NoAvailableGeohashException(e.getMessage(), e);
        }
    }

    /**
     * Second-pass minimum boundary enforcement.
     *
     * Iterates over each Polygon exterior ring (vertices + interpolated edge points)
     * in the FeatureCollection and adds non-clustered center geohashes until
     * {@code minGeohashes} is reached. Unlike the normal pass the strict
     * "any 3x3 overlap → drop" check is relaxed, but the "center already
     * in affectedGeohashes → skip" guard is still applied so that adjacent
     * geohashes are never added next to an existing representative. When a new
     * representative is accepted its full 3×3 grid is reserved in
     * {@code affectedGeohashes}, preventing clustering within this pass too.
     */
    private void enforceMinimumBoundaryGeohashes(
            GeofenceFeatureCollection geofenceFeatureCollection,
            int gridPrecision,
            int gridSize,
            double latStep,
            double lonStep,
            Set<String> affectedGeohashes,
            List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes,
            int minGeohashes) {
        if (geofenceFeatureCollection == null || geofenceFeatureCollection.getFeatures() == null) {
            return;
        }

        outer:
        for (var feature : geofenceFeatureCollection.getFeatures()) {
            if (feature.getGeometry() == null) {
                continue;
            }
            if (!(feature.getGeometry() instanceof usdot.v2x.app.api.models.etx.configuration.geometry.Polygon)) {
                continue;
            }
            usdot.v2x.app.api.models.etx.configuration.geometry.Polygon polygon =
                    (usdot.v2x.app.api.models.etx.configuration.geometry.Polygon) feature.getGeometry();

            if (polygon.getCoordinates() == null || polygon.getCoordinates().isEmpty()) {
                continue;
            }

            // Exterior ring is index 0: List<List<Double>> where each inner list is [lon, lat]
            List<List<Double>> ring = polygon.getCoordinates().get(0);
            if (ring == null || ring.size() < 2) {
                continue;
            }

            for (int i = 0; i < ring.size() - 1; i++) {
                List<Double> from = ring.get(i);
                List<Double> to = ring.get(i + 1);
                if (from == null || from.size() < 2 || to == null || to.size() < 2) {
                    continue;
                }

                double fromLon = from.get(0);
                double fromLat = from.get(1);
                double toLon = to.get(0);
                double toLat = to.get(1);

                // Vertex itself
                if (addBoundaryGeohash(fromLat, fromLon, gridPrecision, gridSize, latStep, lonStep,
                        affectedGeohashes, representativeGeohashes, existingUsedGeohashes)
                        && representativeGeohashes.size() >= minGeohashes) {
                    break outer;
                }

                // Interpolated edge points
                Coordinate startCoord = new Coordinate(fromLon, fromLat);
                Coordinate endCoord = new Coordinate(toLon, toLat);
                int numPoints = calculateInterpolationPoints(startCoord, endCoord);
                for (int j = 1; j < numPoints; j++) {
                    double ratio = (double) j / numPoints;
                    double lat = fromLat + (toLat - fromLat) * ratio;
                    double lon = fromLon + (toLon - fromLon) * ratio;
                    if (addBoundaryGeohash(lat, lon, gridPrecision, gridSize, latStep, lonStep,
                            affectedGeohashes, representativeGeohashes, existingUsedGeohashes)
                            && representativeGeohashes.size() >= minGeohashes) {
                        break outer;
                    }
                }
            }
        }
    }

    /**
     * Encodes {@code (lat, lon)} and adds it to {@code representativeGeohashes} if:
     * <ul>
     *   <li>its center geohash is not already covered by any 3×3 grid in
     *       {@code affectedGeohashes} (prevents clustering), and</li>
     *   <li>it is not claimed by another active deployment
     *       ({@code existingUsedGeohashes}).</li>
     * </ul>
     * On success the representative's 3×3 grid is reserved in
     * {@code affectedGeohashes} so subsequent candidates in the same pass are
     * spaced correctly.
     *
     * @return {@code true} if the geohash was newly added, {@code false} otherwise
     */
    private boolean addBoundaryGeohash(double lat, double lon,
            int gridPrecision, int gridSize, double latStep, double lonStep,
            Set<String> affectedGeohashes,
            List<String> representativeGeohashes,
            Set<String> existingUsedGeohashes) {
        try {
            String geohash = encodeGeohash(lat, lon, gridPrecision);

            // Skip if this point's cell is already inside an existing 3x3 grid
            if (affectedGeohashes.contains(geohash)) {
                return false;
            }
            // When allowOverlappingGeohashes is true, cross-deployment sharing is
            // permitted, so existingUsedGeohashes is ignored.
            boolean allowSharing = geofenceProperties.getLimits().isAllowOverlappingGeohashes();
            if (!allowSharing && existingUsedGeohashes != null && existingUsedGeohashes.contains(geohash)) {
                return false;
            }

            // Claim the 3x3 grid so the next candidate in this pass stays spaced
            List<String> grid = generate3x3GridOptimized(lat, lon, gridPrecision, gridSize, latStep, lonStep);
            affectedGeohashes.addAll(grid);
            representativeGeohashes.add(geohash);
            return true;
        } catch (Exception e) {
            log.warn("Error encoding boundary geohash at ({}, {}): {}", lat, lon, e.getMessage());
        }
        return false;
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