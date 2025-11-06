package usdot.v2x.app.api.utils;

import org.locationtech.jts.geom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import usdot.v2x.app.api.models.etx.configuration.geometry.Polygon;

import java.util.HashMap;
import java.util.List;

/**
 * Test class for GeometryConverter utility.
 * Verifies that JTS geometry objects can be converted to custom geometry
 * objects and vice versa.
 */
public class GeometryConverterTest {

    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        geometryFactory = GeometryUtils.getGeometryFactory();
    }

    @Test
    void testPolygonConversion() {
        // Create a simple JTS Polygon
        Coordinate[] coordinates = {
                new Coordinate(0.0, 0.0),
                new Coordinate(0.0, 1.0),
                new Coordinate(1.0, 1.0),
                new Coordinate(1.0, 0.0),
                new Coordinate(0.0, 0.0) // Close the ring
        };

        LinearRing ring = geometryFactory.createLinearRing(coordinates);
        org.locationtech.jts.geom.Polygon jtsPolygon = geometryFactory.createPolygon(ring);

        // Convert to custom geometry
        usdot.v2x.app.api.models.etx.configuration.geometry.Geometry customGeometry = GeometryConverter
                .fromJtsGeometry(jtsPolygon);

        // Verify it's a Polygon
        assertTrue(customGeometry instanceof Polygon);
        Polygon customPolygon = (Polygon) customGeometry;

        // Verify coordinates
        List<List<List<Double>>> coords = customPolygon.getCoordinates();
        assertNotNull(coords);
        assertEquals(1, coords.size()); // One ring (exterior)

        List<List<Double>> exteriorRing = coords.get(0);
        assertEquals(5, exteriorRing.size()); // 5 points including closing point

        // Verify first point (should be lon=0, lat=0)
        List<Double> firstPoint = exteriorRing.get(0);
        assertEquals(0.0, firstPoint.get(0), 0.001); // longitude
        assertEquals(0.0, firstPoint.get(1), 0.001); // latitude
    }

    @Test
    void testCustomPolygonToJtsConversion() {
        // Create a custom Polygon
        Polygon customPolygon = new Polygon();

        // Create coordinates for a simple rectangle
        List<List<List<Double>>> coordinates = List.of(
                List.of(
                        List.of(0.0, 0.0), // lon, lat
                        List.of(0.0, 1.0),
                        List.of(1.0, 1.0),
                        List.of(1.0, 0.0),
                        List.of(0.0, 0.0) // Close the ring
                ));

        customPolygon.setCoordinates(coordinates);

        // Convert to JTS
        Geometry jtsGeometry = GeometryConverter.toJtsGeometry(customPolygon);

        // Verify it's a Polygon
        assertTrue(jtsGeometry instanceof org.locationtech.jts.geom.Polygon);
        org.locationtech.jts.geom.Polygon jtsPolygon = (org.locationtech.jts.geom.Polygon) jtsGeometry;

        // Verify coordinates
        Coordinate[] coords = jtsPolygon.getExteriorRing().getCoordinates();
        assertEquals(5, coords.length);

        // Verify first point
        assertEquals(0.0, coords[0].x, 0.001); // longitude
        assertEquals(0.0, coords[0].y, 0.001); // latitude
    }

    @Test
    void testGeofenceFeatureHelper() {
        // Create a JTS Polygon
        Coordinate[] coordinates = {
                new Coordinate(0.0, 0.0),
                new Coordinate(0.0, 1.0),
                new Coordinate(1.0, 1.0),
                new Coordinate(1.0, 0.0),
                new Coordinate(0.0, 0.0)
        };

        LinearRing ring = geometryFactory.createLinearRing(coordinates);
        org.locationtech.jts.geom.Polygon jtsPolygon = geometryFactory.createPolygon(ring);

        // Create a GeofenceFeature using the helper
        GeofenceFeature feature = GeofenceFeatureHelper.createFeature("Feature", jtsPolygon, new HashMap<>());

        // Verify the feature was created correctly
        assertNotNull(feature);
        assertEquals("Feature", feature.getType());
        assertNotNull(feature.getGeometry());
        assertTrue(feature.getGeometry() instanceof Polygon);

        // Verify we can get the JTS geometry back
        Geometry retrievedJtsGeometry = GeofenceFeatureHelper.getJtsGeometry(feature);
        assertNotNull(retrievedJtsGeometry);
        assertTrue(retrievedJtsGeometry instanceof org.locationtech.jts.geom.Polygon);
    }

    @Test
    void testSetGeometryOnFeature() {
        // Create a JTS Polygon
        Coordinate[] coordinates = {
                new Coordinate(0.0, 0.0),
                new Coordinate(0.0, 1.0),
                new Coordinate(1.0, 1.0),
                new Coordinate(1.0, 0.0),
                new Coordinate(0.0, 0.0)
        };

        LinearRing ring = geometryFactory.createLinearRing(coordinates);
        org.locationtech.jts.geom.Polygon jtsPolygon = geometryFactory.createPolygon(ring);

        // Create a feature and set geometry using helper
        GeofenceFeature feature = new GeofenceFeature();
        feature.setType("Feature");
        feature.setProperties(new HashMap<>());

        GeofenceFeatureHelper.setGeometry(feature, jtsPolygon);

        // Verify the geometry was set correctly
        assertNotNull(feature.getGeometry());
        assertTrue(feature.getGeometry() instanceof Polygon);
    }
}