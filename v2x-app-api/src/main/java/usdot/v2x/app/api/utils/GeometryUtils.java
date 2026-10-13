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
import java.util.List;

/**
 * Utility class for handling geometric operations and coordinate
 * transformations.
 * This class provides methods for:
 * - Converting between coordinate systems (WGS84/UTM)
 * - Calculating geographic points and distances
 * - Creating and manipulating geometric buffers
 * - Handling J2735 coordinate conversions
 * - Combining multiple polygons while preserving disconnected components
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
     * Calculates the initial bearing from a start point to a destination point.
     * Uses the GeodeticCalculator for accurate ellipsoidal calculations on WGS84.
     * Thread-safe implementation using synchronization.
     *
     * @param startLon Starting longitude in decimal degrees
     * @param startLat Starting latitude in decimal degrees
     * @param endLon   Destination longitude in decimal degrees
     * @param endLat   Destination latitude in decimal degrees
     * @return Bearing angle in degrees (0 = north, 90 = east, 180 = south,
     *         270 = west)
     */
    public static double calculateBearing(double startLon, double startLat, double endLon, double endLat) {
        synchronized (calculator) {
            calculator.setStartingGeographicPoint(startLon, startLat);
            calculator.setDestinationGeographicPoint(endLon, endLat);
            return calculator.getAzimuth();
        }
    }

    /**
     * Calculates the ellipsoidal distance between two geographic points in meters.
     *
     * @param startLon Starting longitude in decimal degrees
     * @param startLat Starting latitude in decimal degrees
     * @param endLon   Destination longitude in decimal degrees
     * @param endLat   Destination latitude in decimal degrees
     * @return Distance in meters
     */
    public static double calculateDistanceMeters(double startLon, double startLat, double endLon, double endLat) {
        synchronized (calculator) {
            calculator.setStartingGeographicPoint(startLon, startLat);
            calculator.setDestinationGeographicPoint(endLon, endLat);
            return calculator.getOrthodromicDistance();
        }
    }

    /**
     * Combines multiple polygons into their exact union geometry.
     * This method handles overlapping and non-overlapping polygons efficiently.
     * 
     * @param polygons List of polygons to combine
     * @return the union as a Polygon or MultiPolygon, preserving disconnected areas
     * @throws RuntimeException if the union operation fails or produces unexpected
     *                          geometry types
     */
    public static Geometry combinePolygons(List<Polygon> polygons) {
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

            // Disconnected inputs legitimately produce a MultiPolygon. Re-unioning the
            // same disconnected components cannot turn them into a Polygon and caused
            // unbounded recursion, so preserve the exact union result.
            if (combinedGeometry instanceof Polygon || combinedGeometry instanceof MultiPolygon) {
                return combinedGeometry;
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
     * Builds a triangular or trapezoidal extension wedge in UTM so it aligns with
     * {@link #bufferGeometryWithUtm(Geometry, double, BufferParameters)} output.
     * Input coordinates use the same (lat, lon) axis order as the buffer helper.
     */
    public static Polygon buildTriangularExtensionWedgeWithUtm(Coordinate endpointLatLon,
            Coordinate pathNeighborLatLon, double corridorHalfWidthMeters, double lengthOffsetMeters,
            double tipWidthMeters) throws Exception {
        return buildTriangularExtensionWedgeWithUtm(endpointLatLon, pathNeighborLatLon, corridorHalfWidthMeters,
                lengthOffsetMeters, tipWidthMeters, 0.0);
    }

    /**
     * Builds a triangular or trapezoidal extension wedge in UTM, with its base
     * moved outward past the endpoint along the path direction. The base spans
     * {@code corridorHalfWidthMeters} on each side of the base centre and the tip
     * sits {@code lengthOffsetMeters} beyond the base centre.
     * Input coordinates use the same (lat, lon) axis order as the buffer helper.
     *
     * @param endpointLatLon          path endpoint in (lat, lon) order
     * @param pathNeighborLatLon      adjacent path vertex in (lat, lon) order, used for direction
     * @param corridorHalfWidthMeters half width of the wedge base
     * @param lengthOffsetMeters      distance from the wedge base to the tip
     * @param tipWidthMeters          width of the tip; 0 gives a pointed tip
     * @param baseOffsetMeters        distance past the endpoint, along the path direction, where the wedge base starts
     * @return the wedge in WGS84 coordinates, (lon, lat) order
     * @throws Exception if coordinate transformation fails
     */
    public static Polygon buildTriangularExtensionWedgeWithUtm(Coordinate endpointLatLon,
            Coordinate pathNeighborLatLon, double corridorHalfWidthMeters, double lengthOffsetMeters,
            double tipWidthMeters, double baseOffsetMeters) throws Exception {
        LineString reference = geometryFactory.createLineString(new Coordinate[] { endpointLatLon, pathNeighborLatLon });
        Point center = reference.getCentroid();
        int utmZone = (int) ((center.getY() + 180) / 6) + 1;
        String epsgCode = String.format("EPSG:%d", center.getX() > 0 ? 32600 + utmZone : 32700 + utmZone);

        CoordinateReferenceSystem utm = CRS.decode(epsgCode);
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326");
        MathTransform toMeters = CRS.findMathTransform(wgs84, utm);
        MathTransform toDegrees = CRS.findMathTransform(utm, wgs84);

        Coordinate endpointUtm = JTS.transform(geometryFactory.createPoint(endpointLatLon), toMeters).getCoordinate();
        Coordinate neighborUtm = JTS.transform(geometryFactory.createPoint(pathNeighborLatLon), toMeters)
                .getCoordinate();

        double dx = endpointUtm.x - neighborUtm.x;
        double dy = endpointUtm.y - neighborUtm.y;
        double segmentLength = Math.hypot(dx, dy);
        if (segmentLength == 0) {
            throw new IllegalArgumentException("Cannot build extension wedge for zero-length segment");
        }

        double dirX = dx / segmentLength;
        double dirY = dy / segmentLength;
        double perpX = -dirY;
        double perpY = dirX;
        double tipHalfWidth = tipWidthMeters / 2.0;

        Coordinate baseCenter = new Coordinate(
                endpointUtm.x + dirX * baseOffsetMeters,
                endpointUtm.y + dirY * baseOffsetMeters);
        Coordinate baseLeft = offsetByPerpendicular(baseCenter, perpX, perpY, -corridorHalfWidthMeters);
        Coordinate baseRight = offsetByPerpendicular(baseCenter, perpX, perpY, corridorHalfWidthMeters);
        Coordinate tipCenter = new Coordinate(
                baseCenter.x + dirX * lengthOffsetMeters,
                baseCenter.y + dirY * lengthOffsetMeters);

        Coordinate[] ringUtm;
        if (tipHalfWidth <= 0) {
            ringUtm = new Coordinate[] { baseLeft, baseRight, tipCenter, baseLeft };
        } else {
            Coordinate tipLeft = offsetByPerpendicular(tipCenter, perpX, perpY, -tipHalfWidth);
            Coordinate tipRight = offsetByPerpendicular(tipCenter, perpX, perpY, tipHalfWidth);
            ringUtm = new Coordinate[] { baseLeft, baseRight, tipRight, tipLeft, baseLeft };
        }

        Polygon wedgeUtm = geometryFactory.createPolygon(geometryFactory.createLinearRing(ringUtm));
        Polygon wedge = (Polygon) JTS.transform(wedgeUtm, toDegrees);
        swapCoordinates(wedge);
        return wedge;
    }

    private static Coordinate offsetByPerpendicular(Coordinate origin, double perpX, double perpY,
            double distanceMeters) {
        return new Coordinate(
                origin.x + perpX * distanceMeters,
                origin.y + perpY * distanceMeters);
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