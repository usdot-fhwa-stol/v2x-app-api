package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Point2D;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferParameters;

class GeometryUtilsTest {

    private static final double DELTA = 0.0001; // Delta for floating point comparisons

    @Test
    void testCalculateDestinationPoint() {
        // Test case: Moving north 1000 meters from Washington DC
        double startLon = -77.0369;
        double startLat = 38.9072;
        double angle = 0; // North
        double distance = 1000; // meters

        Point2D result = GeometryUtils.calculateDestinationPoint(startLon, startLat, angle, distance);

        // Moving north should:
        // 1. Keep longitude roughly the same
        // 2. Increase latitude
        // 3. Move approximately 0.009 degrees latitude (1km ≈ 0.009 degrees at this
        // latitude)
        assertEquals(startLon, result.getX(), DELTA);
        assertTrue(result.getY() > startLat);
        assertEquals(0.009, Math.abs(result.getY() - startLat), DELTA);
    }

    @Test
    void testBufferGeometryWithUtm() throws Exception {
        // Create a point geometry in WGS84
        GeometryFactory factory = GeometryUtils.getGeometryFactory();
        Point point = factory.createPoint(new Coordinate(0, 0.0));

        // Create buffer parameters
        BufferParameters params = GeometryUtils.createBufferParameters(8, BufferParameters.CAP_ROUND);

        // Create a 1000m buffer around the point
        Geometry buffered = GeometryUtils.bufferGeometryWithUtm(point, 1000, params);

        // Basic validation of the buffer
        assertTrue(buffered instanceof Polygon);
        assertTrue(buffered.isValid());

        // Create a copy of the point with swapped coordinates to match the buffered
        // geometry's coordinate order
        Point swappedPoint = factory.createPoint(new Coordinate(0.0, 0.0));
        assertTrue(buffered.contains(swappedPoint));

        // Verify the buffer has reasonable dimensions
        Envelope env = buffered.getEnvelopeInternal();
        double width = Math.abs(env.getMaxX() - env.getMinX());
        double height = Math.abs(env.getMaxY() - env.getMinY());

        // The buffer should be perfectly circular at the equator
        // tolerance is 0.01 to account for projection distortion
        assertEquals(1.0, width / height, 0.01);
    }

    @Test
    void testCreateBufferParameters() {
        int quadrantSegments = 8;
        int endCapStyle = BufferParameters.CAP_ROUND;

        BufferParameters params = GeometryUtils.createBufferParameters(quadrantSegments, endCapStyle);

        assertEquals(quadrantSegments, params.getQuadrantSegments());
        assertEquals(endCapStyle, params.getEndCapStyle());
    }

    @Test
    void testSwapCoordinates() {
        GeometryFactory factory = GeometryUtils.getGeometryFactory();
        double x = -77.0369;
        double y = 38.9072;
        Point point = factory.createPoint(new Coordinate(x, y));

        GeometryUtils.swapCoordinates(point);

        Coordinate[] coords = point.getCoordinates();
        assertEquals(y, coords[0].x, DELTA);
        assertEquals(x, coords[0].y, DELTA);
    }

    @Test
    void testGetGeometryFactory() {
        GeometryFactory factory1 = GeometryUtils.getGeometryFactory();
        GeometryFactory factory2 = GeometryUtils.getGeometryFactory();

        // Should return the same singleton instance
        assertSame(factory1, factory2);
        assertNotNull(factory1);
    }
}