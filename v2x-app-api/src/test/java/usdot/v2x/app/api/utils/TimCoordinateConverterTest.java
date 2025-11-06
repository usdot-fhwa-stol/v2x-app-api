package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.operation.distance.DistanceOp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.config.GeometryProperties;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.*;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;

import java.util.List;

class TimCoordinateConverterTest {
    // Approximately 10 meters at the equator
    private static final double DISTANCE_TOLERANCE = 0.0001;

    private ObjectMapper objectMapper = new ObjectMapper();
    private TimCoordinateConverter converter;

    @BeforeEach
    void setUp() {
        GeometryProperties geometryProperties = new GeometryProperties();
        converter = new TimCoordinateConverter(geometryProperties);
    }

    @Test
    public void testOffsetPathCoordinateConversion_SingleRegion() throws Exception {
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/com/neaera/cvmec/api/utils/SingleRegionPathOffsetTIM.json"));

        TravelerInformationMessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(),
                TravelerInformationMessageFrame.class);

        TravelerInformation tim = messageFrame.getValue();
        List<TravelerDataFrame> dataFrames = tim.getDataFrames();

        TimCoordinateConverter.TimGeometry geometry = converter.convertTimToCoordinates(dataFrames);

        assertNotNull(geometry.pathGeometry);
        assertNotNull(geometry.geofenceGeometry);

        // check length is correct
        assertEquals(4, geometry.pathGeometry.getNumPoints());

        // Verify the anchor point is within tolerance of the geometry
        Position3D anchorPoint = dataFrames.getFirst().getRegions().getFirst().getAnchor();
        Point point = GeometryUtils.getGeometryFactory().createPoint(new Coordinate(
                anchorPoint.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                anchorPoint.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR));
        double distance = DistanceOp.distance(point, geometry.geofenceGeometry);
        assertTrue(distance <= DISTANCE_TOLERANCE,
                "Point should be within " + DISTANCE_TOLERANCE + " degrees of geometry");
    }

    @Test
    public void testOffsetPathCoordinateConversion_MultipleRegions() throws Exception {
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/com/neaera/cvmec/api/utils/MultiRegionPathOffsetTIM.json"));

        TravelerInformationMessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(),
                TravelerInformationMessageFrame.class);

        TravelerInformation tim = messageFrame.getValue();
        List<TravelerDataFrame> dataFrames = tim.getDataFrames();

        TimCoordinateConverter.TimGeometry geometry = converter.convertTimToCoordinates(dataFrames);

        assertNotNull(geometry.pathGeometry);
        assertNotNull(geometry.geofenceGeometry);

        // check length is correct
        assertEquals(16, geometry.pathGeometry.getNumPoints());

        // Verify each region's anchor point is within tolerance of the combined
        // geometry
        for (int i = 0; i < dataFrames.getFirst().getRegions().size(); i++) {
            Position3D anchorPoint = dataFrames.getFirst().getRegions().get(i).getAnchor();
            Point point = GeometryUtils.getGeometryFactory().createPoint(new Coordinate(
                    anchorPoint.getLong_().getValue()
                            / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                    anchorPoint.getLat().getValue()
                            / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR));
            double distance = DistanceOp.distance(point, geometry.geofenceGeometry);
            assertTrue(distance <= DISTANCE_TOLERANCE,
                    "Anchor point for region " + i + " should be within " + DISTANCE_TOLERANCE
                            + " degrees of combined geometry");
        }
    }

    @Test
    public void testOffsetPathCoordinateConversion_SingleRegionCircle() throws Exception {
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/com/neaera/cvmec/api/utils/SingleRegionCircleTIM.json"));

        TravelerInformationMessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(),
                TravelerInformationMessageFrame.class);

        TravelerInformation tim = messageFrame.getValue();
        List<TravelerDataFrame> dataFrames = tim.getDataFrames();

        TimCoordinateConverter.TimGeometry geometry = converter.convertTimToCoordinates(dataFrames);

        assertNotNull(geometry.pathGeometry);
        assertNotNull(geometry.geofenceGeometry);

        // check length is correct
        assertEquals(17, geometry.pathGeometry.getNumPoints());

        // Verify the anchor point is within tolerance of the geometry
        Position3D anchorPoint = dataFrames.getFirst().getRegions().getFirst().getAnchor();
        Point point = GeometryUtils.getGeometryFactory().createPoint(new Coordinate(
                anchorPoint.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                anchorPoint.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR));
        double distance = DistanceOp.distance(point, geometry.geofenceGeometry);
        assertTrue(distance <= DISTANCE_TOLERANCE,
                "Point should be within " + DISTANCE_TOLERANCE + " degrees of geometry");
    }

    @Test
    public void testOffsetPathCoordinateConversion_MultipleDataFrames() throws Exception {
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/com/neaera/cvmec/api/utils/MultiDataFrameOffsetTim.json"));

        TravelerInformationMessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(),
                TravelerInformationMessageFrame.class);

        TravelerInformation tim = messageFrame.getValue();
        List<TravelerDataFrame> dataFrames = tim.getDataFrames();

        TimCoordinateConverter.TimGeometry geometry = converter.convertTimToCoordinates(dataFrames);

        assertNotNull(geometry.pathGeometry);
        assertNotNull(geometry.geofenceGeometry);

        assertEquals(14, geometry.pathGeometry.getNumPoints());

        // Verify each data frame's anchor point is within tolerance of the combined
        // geometry
        for (int i = 0; i < dataFrames.size(); i++) {
            Position3D anchorPoint = dataFrames.get(i).getRegions().getFirst().getAnchor();
            Point point = GeometryUtils.getGeometryFactory().createPoint(new Coordinate(
                    anchorPoint.getLong_().getValue()
                            / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                    anchorPoint.getLat().getValue()
                            / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR));
            double distance = DistanceOp.distance(point, geometry.geofenceGeometry);
            assertTrue(distance <= DISTANCE_TOLERANCE,
                    "Anchor point for data frame " + i + " should be within " + DISTANCE_TOLERANCE
                            + " degrees of combined geometry");
        }
    }

    @Test
    public void testClosedPathCoordinateConversion() throws Exception {
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/com/neaera/cvmec/api/utils/ClosedPathTim.json"));

        TravelerInformationMessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(),
                TravelerInformationMessageFrame.class);

        TravelerInformation tim = messageFrame.getValue();
        List<TravelerDataFrame> dataFrames = tim.getDataFrames();

        TimCoordinateConverter.TimGeometry geometry = converter.convertTimToCoordinates(dataFrames);

        assertNotNull(geometry.pathGeometry);
        assertNotNull(geometry.geofenceGeometry);

        // Verify that the path geometry is not empty
        assertTrue(geometry.pathGeometry.getNumPoints() > 0,
                "Should have path coordinates");

        // Verify each region's anchor point is within tolerance of the combined
        // geometry
        for (int i = 0; i < dataFrames.size(); i++) {
            TravelerDataFrame dataFrame = dataFrames.get(i);
            for (int j = 0; j < dataFrame.getRegions().size(); j++) {
                GeographicalPath region = dataFrame.getRegions().get(j);

                // Only test regions that have closedPath set to true
                if (region.getClosedPath() != null && region.getClosedPath().getValue()) {
                    Position3D anchorPoint = region.getAnchor();
                    if (anchorPoint != null) {
                        Point point = GeometryUtils.getGeometryFactory()
                                .createPoint(new Coordinate(
                                        anchorPoint.getLong_().getValue()
                                                / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                                        anchorPoint.getLat().getValue()
                                                / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR));

                        double distance = DistanceOp.distance(point, geometry.geofenceGeometry);

                        assertTrue(distance <= DISTANCE_TOLERANCE,
                                "Anchor point for closed path region " + i + "." + j
                                        + " should be within "
                                        + DISTANCE_TOLERANCE
                                        + " degrees of combined geometry");
                    }
                }
            }
        }
    }
}
