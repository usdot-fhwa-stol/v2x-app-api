package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.config.GeometryProperties;

import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.MapData.*;

class MapCoordinateConverterTest {

    private ObjectMapper objectMapper = new ObjectMapper();
    private MapCoordinateConverter converter;

    @BeforeEach
    void setUp() {
        GeometryProperties geometryProperties = new GeometryProperties();
        geometryProperties.setGeofenceOffsetMeters(10.0);
        converter = new MapCoordinateConverter(geometryProperties);
    }

    @Test
    public void testMapCoordinateConversion_SingleIntersection() throws Exception {
        JsonNode sampleMap = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/com/neaera/cvmec/api/utils/SingleIntersectionMAP.json"));

        MapDataMessageFrame messageFrame = objectMapper.readValue(sampleMap.toString(),
                MapDataMessageFrame.class);

        MapData map = messageFrame.getValue();
        IntersectionGeometryList intersections = map.getIntersections();

        MapCoordinateConverter.MapGeometry geometry = converter.convertMapToCoordinates(intersections);

        assertNotNull(geometry.geofenceGeometry);
        assertTrue(geometry.geofenceGeometry instanceof Polygon);

        // Verify the geometry bounds
        Polygon polygon = geometry.geofenceGeometry;
        Coordinate[] coords = polygon.getCoordinates();
        assertTrue(coords.length > 0);

        // Verify the refpoint is in the geometry
        Position3D refPoint = map.getIntersections().getFirst().getRefPoint();
        Point point = GeometryUtils.getGeometryFactory().createPoint(new Coordinate(
                refPoint.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                refPoint.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR));
        assertTrue(polygon.contains(point));
    }

    @Test
    public void testMapCoordinateConversion_MultiIntersection() throws Exception {
        JsonNode sampleMap = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/com/neaera/cvmec/api/utils/MultiIntersectionMAP.json"));

        MapDataMessageFrame messageFrame = objectMapper.readValue(sampleMap.toString(),
                MapDataMessageFrame.class);

        MapData map = messageFrame.getValue();
        IntersectionGeometryList intersections = map.getIntersections();

        MapCoordinateConverter.MapGeometry geometry = converter.convertMapToCoordinates(intersections);

        assertNotNull(geometry.geofenceGeometry);
        assertTrue(geometry.geofenceGeometry instanceof Polygon);

        // Verify the combined polygon contains all intersection refpoints
        Polygon combinedPolygon = geometry.geofenceGeometry;
        for (int i = 0; i < intersections.size(); i++) {
            Position3D refPoint = intersections.get(i).getRefPoint();
            Point point = GeometryUtils.getGeometryFactory().createPoint(new Coordinate(
                    refPoint.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                    refPoint.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR));
            assertTrue(combinedPolygon.contains(point),
                    "Refpoint for intersection " + i + " should be contained in the combined geometry");
        }
    }

    @Test
    public void testMapCoordinateConversion_NoIntersections() {
        IntersectionGeometryList emptyList = new IntersectionGeometryList();

        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertMapToCoordinates(emptyList);
        }, "MAP message must contain at least one intersection");
    }

    @Test
    public void testMapCoordinateConversion_NoRefPoint() {
        IntersectionGeometryList intersections = new IntersectionGeometryList();
        IntersectionGeometry intersection = new IntersectionGeometry();
        intersections.add(intersection);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            converter.convertMapToCoordinates(intersections);
        });
        assertEquals("No valid intersections found in MAP message", exception.getMessage());
    }
}
