package usdot.v2x.app.api.utils;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.referencing.GeodeticCalculator;
import org.springframework.stereotype.Component;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling geometric operations and coordinate
 * transformations.
 * This class provides methods for:
 * - Converting between coordinate systems (WGS84/UTM)
 * - Calculating geographic points and distances
 * - Creating and manipulating geometric buffers
 * - Handling J2735 coordinate conversions
 * - Combining multiple polygons into a single polygon
 */
@Component
public class GeometryUtils {
    private static final GeometryFactory geometryFactory = new GeometryFactory();
    /**
     * Conversion factor for J2735 coordinates.
     * J2735 uses 1/10 microdegree for Lat/Lon coordinates.
     * To convert J2735 integer values to decimal degrees, divide by this factor.
     * Example: 123456789 / 10000000 = 12.3456789 degrees
     */
    public static final double J2735_DECIMAL_CONVERSION_FACTOR = 10000000.0;
    /**
     * Thread-safe calculator for geodetic operations.
     * Used for accurate distance and bearing calculations on the WGS84 ellipsoid.
     */
    private static final GeodeticCalculator calculator = new GeodeticCalculator();

    /**
     * Calculates a destination point given a start point, angle and distance.
     * Uses the GeodeticCalculator for accurate ellipsoidal calculations on WGS84.
     * Thread-safe implementation using synchronization.
     * 
     * @param startLon Starting longitude in decimal degrees
     * @param startLat Starting latitude in decimal degrees
     * @param angle    Bearing angle in degrees (0 = north, 90 = east, 180 = south,
     *                 270 = west)
     * @param distance Distance in meters
     * @return Point2D containing destination coordinates (x=longitude, y=latitude)
     */
    public static Point2D calculateDestinationPoint(double startLon, double startLat, double angle, double distance) {
        synchronized (calculator) {
            calculator.setStartingGeographicPoint(startLon, startLat);
            calculator.setDirection(angle, distance);
            return calculator.getDestinationGeographicPoint();
        }
    }

    /**
     * Combines multiple polygons into a single polygon using union operations.
     * This method handles overlapping and non-overlapping polygons efficiently.
     * 
     * @param polygons List of polygons to combine
     * @return A single polygon that encompasses all input polygons
     * @throws RuntimeException if the union operation fails or produces unexpected
     *                          geometry types
     */
    public static Polygon combinePolygons(List<Polygon> polygons) {
        if (polygons.isEmpty()) {
            // Return a default empty polygon if no polygons exist
            return geometryFactory.createPolygon();
        }

        if (polygons.size() == 1) {
            // Return the single polygon as is
            return polygons.get(0);
        }

        try {
            // Use UnaryUnionOp to combine all polygons into a single geometry
            // This handles overlapping and non-overlapping polygons efficiently
            Geometry combinedGeometry = UnaryUnionOp.union(polygons);

            // If the result is a polygon, return it directly
            if (combinedGeometry instanceof Polygon) {
                return (Polygon) combinedGeometry;
            }

            // If the result is a MultiPolygon, we need to extract the first polygon
            // or create a union of all polygons in the MultiPolygon
            if (combinedGeometry instanceof MultiPolygon) {
                MultiPolygon multiPolygon = (MultiPolygon) combinedGeometry;
                if (multiPolygon.getNumGeometries() == 1) {
                    return (Polygon) multiPolygon.getGeometryN(0);
                } else {
                    // If we have multiple polygons after union, try to union them again
                    // This can happen with complex geometries
                    List<Polygon> remainingPolygons = new ArrayList<>();
                    for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                        Geometry geom = multiPolygon.getGeometryN(i);
                        if (geom instanceof Polygon) {
                            remainingPolygons.add((Polygon) geom);
                        }
                    }
                    return combinePolygons(remainingPolygons);
                }
            }

            // If we get here, the result is not a polygon or MultiPolygon
            // This should not happen with valid polygon inputs, but handle gracefully
            throw new RuntimeException("Unexpected geometry type after union: " + combinedGeometry.getGeometryType());

        } catch (Exception e) {
            throw new RuntimeException("Error combining polygons", e);
        }
    }

    /**
     * Transforms a geometry to UTM coordinates, applies a buffer, and transforms
     * back to WGS84.
     * This method handles the following steps:
     * 1. Determines the appropriate UTM zone based on the geometry's centroid
     * 2. Projects the geometry to UTM for accurate metric calculations
     * 3. Applies the buffer operation in meters
     * 4. Projects the result back to WGS84 (EPSG:4326)
     * 5. Swaps coordinates to maintain GeoJSON compatibility (lon,lat order)
     *
     * @param geometry             The input geometry in WGS84 coordinates
     * @param bufferDistanceMeters The buffer distance in meters
     * @param bufferParams         Buffer operation parameters (segments, end cap
     *                             style, etc.)
     * @return The buffered geometry in WGS84 coordinates
     * @throws Exception If coordinate transformation fails
     */
    public static Geometry bufferGeometryWithUtm(Geometry geometry, double bufferDistanceMeters,
            BufferParameters bufferParams) throws Exception {
        // Get the center point for the local projection
        Point center = geometry.getCentroid();

        // Calculate UTM zone number:
        // - Earth is divided into 60 zones, each 6° wide
        // - Zone 1 starts at -180° (longitude)
        // - Formula: ((longitude + 180) / 6) + 1
        int utmZone = (int) ((center.getY() + 180) / 6) + 1; // use Y for longitude

        // Create EPSG code for the UTM zone:
        // - UTM codes are in format: EPSG:326XX or EPSG:327XX
        // - 326XX: Northern hemisphere (latitude > 0)
        // - 327XX: Southern hemisphere (latitude <= 0)
        // - XX: UTM zone number (01-60)
        // Examples:
        // - Zone 17N: EPSG:32617
        // - Zone 17S: EPSG:32717
        String epsgCode = String.format("EPSG:%d", center.getX() > 0 ? 32600 + utmZone : 32700 + utmZone);

        // Create coordinate reference systems:
        // - UTM: Local projection for accurate distance measurements
        // - WGS84: Global lat/lon system (EPSG:4326)
        CoordinateReferenceSystem utm = CRS.decode(epsgCode);
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326");

        // Create transform operations:
        // - toMeters: WGS84 → UTM (converts lat/lon to meters)
        // - toDegrees: UTM → WGS84 (converts meters back to lat/lon)
        MathTransform toMeters = CRS.findMathTransform(wgs84, utm);
        MathTransform toDegrees = CRS.findMathTransform(utm, wgs84);

        // Transform geometry to UTM (now in meters), apply buffer, transform back to
        // WGS84
        Geometry utmGeom = JTS.transform(geometry, toMeters);
        Geometry buffered = BufferOp.bufferOp(utmGeom, bufferDistanceMeters, bufferParams);
        Geometry result = JTS.transform(buffered, toDegrees);

        // Swap coordinates to maintain GeoJSON compatibility:
        // - GeoJSON expects coordinates in [longitude, latitude] order
        // - Most geographic operations use [latitude, longitude] order
        swapCoordinates(result);

        return result;
    }

    /**
     * Creates buffer parameters with common settings for geometric operations.
     * Used to configure the detail and style of buffer operations.
     *
     * @param quadrantSegments Number of segments used to approximate a quarter
     *                         circle (more = smoother)
     * @param endCapStyle      The style of the buffer end caps (ROUND, FLAT,
     *                         SQUARE)
     * @return BufferParameters configured with the specified settings
     */
    public static BufferParameters createBufferParameters(int quadrantSegments, int endCapStyle) {
        BufferParameters params = new BufferParameters();
        params.setQuadrantSegments(quadrantSegments);
        params.setEndCapStyle(endCapStyle);
        return params;
    }

    /**
     * Swaps X and Y coordinates in a geometry.
     * Used to convert between (lat,lon) and (lon,lat) coordinate ordering.
     * This is necessary for compatibility between different coordinate standards:
     * - GeoJSON uses (lon,lat) order
     * - Many geographic systems use (lat,lon) order
     *
     * @param geometry The geometry whose coordinates should be swapped
     */
    public static void swapCoordinates(Geometry geometry) {
        Coordinate[] coords = geometry.getCoordinates();
        for (Coordinate coord : coords) {
            double temp = coord.x;
            coord.x = coord.y;
            coord.y = temp;
        }
    }

    /**
     * Returns the singleton GeometryFactory instance used for creating JTS
     * geometric objects.
     * This factory is thread-safe and can be used across multiple operations.
     *
     * @return The shared GeometryFactory instance
     */
    public static GeometryFactory getGeometryFactory() {
        return geometryFactory;
    }
}