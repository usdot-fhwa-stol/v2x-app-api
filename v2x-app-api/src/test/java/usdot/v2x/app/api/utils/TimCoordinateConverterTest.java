package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.distance.DistanceOp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.config.GeometryProperties;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.*;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;

import java.awt.geom.Point2D;
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
                        "/usdot/v2x/app/api/utils/SingleRegionPathOffsetTIM.json"));

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
                        "/usdot/v2x/app/api/utils/MultiRegionPathOffsetTIM.json"));

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
                        "/usdot/v2x/app/api/utils/SingleRegionCircleTIM.json"));

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
                        "/usdot/v2x/app/api/utils/MultiDataFrameOffsetTim.json"));

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
                        "/usdot/v2x/app/api/utils/ClosedPathTim.json"));

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

    @Test
    public void eastWestPathLengthOffsetExtendsBothEndsWithoutWidening() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double lengthOffsetMeters = 300.0;
        TimCoordinateConverter.TimGeometry withoutOffset = convertOpenPath(dataFrames, 0.0, 0.0);
        TimCoordinateConverter.TimGeometry withOffset = convertOpenPath(dataFrames, 0.0, lengthOffsetMeters);

        assertEquals(2, withoutOffset.pathGeometry.getNumPoints());
        assertEquals(withoutOffset.pathGeometry.getNumPoints(), withOffset.pathGeometry.getNumPoints());

        Envelope baseEnvelope = withoutOffset.geofenceGeometry.getEnvelopeInternal();
        Envelope extendedEnvelope = withOffset.geofenceGeometry.getEnvelopeInternal();

        double extraLengthMeters = lonDeltaToMeters(extendedEnvelope.getWidth() - baseEnvelope.getWidth(), 40.0);
        double extraWidthMeters = latDeltaToMeters(extendedEnvelope.getHeight() - baseEnvelope.getHeight());

        assertEquals(2 * lengthOffsetMeters, extraLengthMeters, 25.0,
                "Each end of an east-west path should grow by the configured length offset");
        assertEquals(0.0, extraWidthMeters, 5.0,
                "Length offset must not change east-west path geofence width");

        Coordinate start = withoutOffset.pathGeometry.getCoordinateN(0);
        Coordinate end = withoutOffset.pathGeometry.getCoordinateN(withoutOffset.pathGeometry.getNumPoints() - 1);

        Point westOfStart = destinationPoint(start, 270, 100);
        Point eastOfEnd = destinationPoint(end, 90, 100);
        Point northOfMid = destinationPoint(midpoint(start, end), 0, 50);

        assertFalse(covers(withoutOffset.geofenceGeometry, westOfStart),
                "100m west of path start should be outside without length offset");
        assertFalse(covers(withoutOffset.geofenceGeometry, eastOfEnd),
                "100m east of path end should be outside without length offset");
        assertTrue(covers(withOffset.geofenceGeometry, westOfStart),
                "100m west of path start should be inside with 300m length offset");
        assertTrue(covers(withOffset.geofenceGeometry, eastOfEnd),
                "100m east of path end should be inside with 300m length offset");
        assertFalse(covers(withoutOffset.geofenceGeometry, northOfMid),
                "50m north of path should stay outside without length offset");
        assertFalse(covers(withOffset.geofenceGeometry, northOfMid),
                "50m north of path should stay outside with length offset");
    }

    @Test
    public void northSouthPathLengthOffsetExtendsBothEndsWithoutWidening() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("NorthSouthPathOffsetTIM.json");
        double lengthOffsetMeters = 300.0;
        TimCoordinateConverter.TimGeometry withoutOffset = convertOpenPath(dataFrames, 0.0, 0.0);
        TimCoordinateConverter.TimGeometry withOffset = convertOpenPath(dataFrames, 0.0, lengthOffsetMeters);

        Envelope baseEnvelope = withoutOffset.geofenceGeometry.getEnvelopeInternal();
        Envelope extendedEnvelope = withOffset.geofenceGeometry.getEnvelopeInternal();

        double extraLengthMeters = latDeltaToMeters(extendedEnvelope.getHeight() - baseEnvelope.getHeight());
        double extraWidthMeters = lonDeltaToMeters(extendedEnvelope.getWidth() - baseEnvelope.getWidth(), 40.0);

        assertEquals(2 * lengthOffsetMeters, extraLengthMeters, 25.0,
                "Each end of a north-south path should grow by the configured length offset");
        assertEquals(0.0, extraWidthMeters, 5.0,
                "Length offset must not change north-south path geofence width");

        Coordinate start = withoutOffset.pathGeometry.getCoordinateN(0);
        Coordinate end = withoutOffset.pathGeometry.getCoordinateN(withoutOffset.pathGeometry.getNumPoints() - 1);

        Point southOfStart = destinationPoint(start, 180, 100);
        Point northOfEnd = destinationPoint(end, 0, 100);
        Point eastOfMid = destinationPoint(midpoint(start, end), 90, 50);

        assertFalse(covers(withoutOffset.geofenceGeometry, southOfStart));
        assertFalse(covers(withoutOffset.geofenceGeometry, northOfEnd));
        assertTrue(covers(withOffset.geofenceGeometry, southOfStart),
                "100m south of path start should be inside with 300m length offset");
        assertTrue(covers(withOffset.geofenceGeometry, northOfEnd),
                "100m north of path end should be inside with 300m length offset");
        assertFalse(covers(withOffset.geofenceGeometry, eastOfMid),
                "50m east of path should stay outside with length offset");
    }

    @Test
    public void pathLengthOffsetCoversPointJustInsideConfiguredDistance() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double lengthOffsetMeters = 300.0;
        double widthBufferMeters = 5.0; // fixture laneWidth is 500 cm
        TimCoordinateConverter.TimGeometry withoutOffset = convertOpenPath(dataFrames, 0.0, 0.0);
        TimCoordinateConverter.TimGeometry withOffset = convertOpenPath(dataFrames, 0.0, lengthOffsetMeters);

        Coordinate end = withoutOffset.pathGeometry.getCoordinateN(withoutOffset.pathGeometry.getNumPoints() - 1);
        Point justInside = destinationPoint(end, 90, lengthOffsetMeters - 20);
        Point justOutside = destinationPoint(end, 90, lengthOffsetMeters + 50);

        assertFalse(covers(withoutOffset.geofenceGeometry, justInside),
                "Point 280m beyond path end should be outside without length offset");
        assertTrue(covers(withOffset.geofenceGeometry, justInside),
                "Point 280m beyond path end should be inside with 300m length offset");
        assertFalse(covers(withOffset.geofenceGeometry, justOutside),
                "Point beyond the triangular extension tip should remain outside");
    }

    @Test
    public void triangularExtensionTipIsNarrowerThanCorridor() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double lengthOffsetMeters = 300.0;
        double corridorHalfWidthMeters = 55.0;
        TimCoordinateConverter.TimGeometry withOffset = convertOpenPath(dataFrames, corridorHalfWidthMeters,
                lengthOffsetMeters, 0.0);

        Coordinate end = withOffset.pathGeometry.getCoordinateN(withOffset.pathGeometry.getNumPoints() - 1);
        Point widePoint = destinationPoint(end, 90, lengthOffsetMeters - 20);
        Point2D wideOffset = GeometryUtils.calculateDestinationPoint(widePoint.getCoordinate().x,
                widePoint.getCoordinate().y, 0, corridorHalfWidthMeters + 20);
        Point northOfTip = GeometryUtils.getGeometryFactory()
                .createPoint(new Coordinate(wideOffset.getX(), wideOffset.getY()));

        assertTrue(covers(withOffset.geofenceGeometry, widePoint),
                "Center of extension should remain covered");
        assertFalse(covers(withOffset.geofenceGeometry, northOfTip),
                "Point beyond corridor width near the extension tip should stay outside a pointed triangle");
    }

    @Test
    public void triangularExtensionTipWidthWidensTip() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double lengthOffsetMeters = 300.0;
        double geofenceOffsetMeters = 55.0;
        double corridorHalfWidthMeters = 5.0 + geofenceOffsetMeters; // fixture laneWidth is 500 cm, plus offset
        double tipWidthMeters = 40.0;
        TimCoordinateConverter.TimGeometry pointed = convertOpenPath(dataFrames, geofenceOffsetMeters,
                lengthOffsetMeters, 0.0);
        TimCoordinateConverter.TimGeometry blunt = convertOpenPath(dataFrames, geofenceOffsetMeters,
                lengthOffsetMeters, tipWidthMeters);

        Coordinate end = pointed.pathGeometry.getCoordinateN(pointed.pathGeometry.getNumPoints() - 1);
        // Probe 2 m inside the real tip, which sits corridor half width plus length offset past the end
        Point tipCenter = destinationPoint(end, 90, corridorHalfWidthMeters + lengthOffsetMeters - 2.0);
        Point2D tipOffset = GeometryUtils.calculateDestinationPoint(tipCenter.getCoordinate().x,
                tipCenter.getCoordinate().y, 0, tipWidthMeters / 2.0 - 5.0);
        Point nearTipEdge = GeometryUtils.getGeometryFactory()
                .createPoint(new Coordinate(tipOffset.getX(), tipOffset.getY()));

        assertFalse(covers(pointed.geofenceGeometry, nearTipEdge),
                "Pointed triangle should not cover the configured tip width");
        assertTrue(covers(blunt.geofenceGeometry, nearTipEdge),
                "Blunt tip should cover points within half the configured tip width");
        assertTrue(blunt.geofenceGeometry.getArea() > pointed.geofenceGeometry.getArea(),
                "A wider tip should increase extension area");
    }

    @Test
    public void unsetPathLengthOffsetMatchesExplicitZeroAndDiffersFromPositiveOffset() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");

        GeometryProperties unsetLengthOffset = new GeometryProperties();
        unsetLengthOffset.setGeofenceOffsetMeters(10.0);
        TimCoordinateConverter.TimGeometry legacy = new TimCoordinateConverter(unsetLengthOffset)
                .convertTimToCoordinates(dataFrames);

        TimCoordinateConverter.TimGeometry explicitZero = convertOpenPath(dataFrames, 10.0, 0.0);
        TimCoordinateConverter.TimGeometry withOffset = convertOpenPath(dataFrames, 10.0, 300.0);

        assertEquals(0.0, unsetLengthOffset.getGeofencePathLengthOffsetMeters());
        assertTrue(legacy.geofenceGeometry.equalsExact(explicitZero.geofenceGeometry, 1e-12));
        assertFalse(legacy.geofenceGeometry.equalsExact(withOffset.geofenceGeometry, 1e-12),
                "A positive path length offset should change the open-path geofence");
    }

    @Test
    public void pathLengthOffsetIsAdditiveToExistingWidthBuffer() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double lengthOffsetMeters = 300.0;
        double geofenceOffsetMeters = 50.0;
        TimCoordinateConverter.TimGeometry widthOnly = convertOpenPath(dataFrames, geofenceOffsetMeters, 0.0);
        TimCoordinateConverter.TimGeometry widthAndLength = convertOpenPath(dataFrames, geofenceOffsetMeters,
                lengthOffsetMeters);

        Envelope widthOnlyEnvelope = widthOnly.geofenceGeometry.getEnvelopeInternal();
        Envelope bothEnvelope = widthAndLength.geofenceGeometry.getEnvelopeInternal();

        double extraLengthMeters = lonDeltaToMeters(bothEnvelope.getWidth() - widthOnlyEnvelope.getWidth(), 40.0);
        double extraWidthMeters = latDeltaToMeters(bothEnvelope.getHeight() - widthOnlyEnvelope.getHeight());

        assertEquals(2 * lengthOffsetMeters, extraLengthMeters, 30.0,
                "Triangular extension should add configured length beyond square-cap corridor ends");
        assertEquals(0.0, extraWidthMeters, 5.0,
                "Adding a length offset must not widen a geofence that already has a width buffer");
    }

    @Test
    public void pathLengthOffsetDoesNotChangeClosedPathGeofence() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("ClosedPathTim.json");
        TimCoordinateConverter.TimGeometry withoutOffset = convertOpenPath(dataFrames, 10.0, 0.0);
        TimCoordinateConverter.TimGeometry withOffset = convertOpenPath(dataFrames, 10.0, 500.0);

        assertTrue(withoutOffset.geofenceGeometry.equalsExact(withOffset.geofenceGeometry, 1e-12),
                "Closed-path TIM geofences should ignore GEOFENCE_PATH_LENGTH_OFFSET_METERS");
    }

    @Test
    public void openPathEndWithoutExtensionKeepsFullWidthSquareCap() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("ThreeRegionEastWestPathTIM.json");
        double geofenceOffsetMeters = 10.0;
        double corridorHalfWidthMeters = 5.0 + geofenceOffsetMeters; // fixture laneWidth is 500 cm
        TimCoordinateConverter.TimGeometry geometry = convertOpenPath(dataFrames, geofenceOffsetMeters, 30.0);
        Coordinate[] path = geometry.pathGeometry.getCoordinates();

        // Region 0 is START-only, so its END (path[1]) keeps only the square cap
        assertTrue(covers(geometry.geofenceGeometry,
                beyondEndpoint(path[0], path[1], corridorHalfWidthMeters - 1)),
                "Non-extended END of the first region should keep its full-width square cap");
        assertFalse(covers(geometry.geofenceGeometry,
                beyondEndpoint(path[0], path[1], corridorHalfWidthMeters + 5)),
                "Non-extended END of the first region should not extend past the square cap");

        // Region 2 is END-only, so its START (path[4]) keeps only the square cap
        assertTrue(covers(geometry.geofenceGeometry,
                beyondEndpoint(path[5], path[4], corridorHalfWidthMeters - 1)),
                "Non-extended START of the last region should keep its full-width square cap");
        assertFalse(covers(geometry.geofenceGeometry,
                beyondEndpoint(path[5], path[4], corridorHalfWidthMeters + 5)),
                "Non-extended START of the last region should not extend past the square cap");
    }

    @Test
    public void extendedEndsReachCorridorHalfWidthPlusLengthOffsetAlongCenterline() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double geofenceOffsetMeters = 10.0;
        double lengthOffsetMeters = 300.0;
        double corridorHalfWidthMeters = 5.0 + geofenceOffsetMeters; // fixture laneWidth is 500 cm
        double reachMeters = corridorHalfWidthMeters + lengthOffsetMeters;
        TimCoordinateConverter.TimGeometry geometry = convertOpenPath(dataFrames, geofenceOffsetMeters,
                lengthOffsetMeters);
        Coordinate[] path = geometry.pathGeometry.getCoordinates();
        Coordinate start = path[0];
        Coordinate startNeighbor = path[1];
        Coordinate end = path[path.length - 1];
        Coordinate endNeighbor = path[path.length - 2];

        assertTrue(covers(geometry.geofenceGeometry, beyondEndpoint(startNeighbor, start, reachMeters - 1)),
                "Start should reach corridor half width plus length offset");
        assertFalse(covers(geometry.geofenceGeometry, beyondEndpoint(startNeighbor, start, reachMeters + 5)),
                "Start should not reach beyond corridor half width plus length offset");
        assertTrue(covers(geometry.geofenceGeometry, beyondEndpoint(endNeighbor, end, reachMeters - 1)),
                "End should reach corridor half width plus length offset");
        assertFalse(covers(geometry.geofenceGeometry, beyondEndpoint(endNeighbor, end, reachMeters + 5)),
                "End should not reach beyond corridor half width plus length offset");
    }

    @Test
    public void extendedGeofenceCoversPlainSquareCapCorridor() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double geofenceOffsetMeters = 10.0;
        double corridorHalfWidthMeters = 5.0 + geofenceOffsetMeters; // fixture laneWidth is 500 cm
        TimCoordinateConverter.TimGeometry extended = convertOpenPath(dataFrames, geofenceOffsetMeters, 300.0);

        Geometry plain = plainSquareCapCorridor(extended.pathGeometry.getCoordinates(), corridorHalfWidthMeters);

        // Union with the wedges re-nodes the cap edges, so allow ~0.1 mm of floating-point slack
        assertTrue(extended.geofenceGeometry.buffer(1e-9).covers(plain),
                "Extended geofence should contain the plain square-cap corridor");
    }

    @Test
    public void multiRegionExtendedGeofenceCoversPlainSquareCapCorridorOfEachRegion() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("ThreeRegionEastWestPathTIM.json");
        double geofenceOffsetMeters = 10.0;
        double corridorHalfWidthMeters = 5.0 + geofenceOffsetMeters; // fixture laneWidth is 500 cm
        TimCoordinateConverter.TimGeometry extended = convertOpenPath(dataFrames, geofenceOffsetMeters, 30.0);
        Coordinate[] path = extended.pathGeometry.getCoordinates();

        for (int region = 0; region < 3; region++) {
            Coordinate[] regionPath = { path[2 * region], path[2 * region + 1] };
            Geometry plain = plainSquareCapCorridor(regionPath, corridorHalfWidthMeters);
            assertTrue(extended.geofenceGeometry.buffer(1e-9).covers(plain),
                    "Geofence should contain the plain square-cap corridor of region " + region);
        }
    }

    @Test
    public void zeroPathLengthOffsetKeepsPlainSquareCapCorridor() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("EastWestPathOffsetTIM.json");
        double geofenceOffsetMeters = 10.0;
        TimCoordinateConverter.TimGeometry geometry = convertOpenPath(dataFrames, geofenceOffsetMeters, 0.0);

        Geometry plain = plainSquareCapCorridor(geometry.pathGeometry.getCoordinates(),
                5.0 + geofenceOffsetMeters); // fixture laneWidth is 500 cm

        assertTrue(geometry.geofenceGeometry.equalsExact(plain, 1e-9),
                "Without a length offset the geofence should be the plain square-cap corridor");
    }

    @Test
    public void pathLengthExtensionForIdentifiesFirstLastAndMiddle() {
        assertEquals(TimCoordinateConverter.PathLengthExtension.BOTH,
                TimCoordinateConverter.pathLengthExtensionFor(0, 0, 0));
        assertEquals(TimCoordinateConverter.PathLengthExtension.START,
                TimCoordinateConverter.pathLengthExtensionFor(0, 0, 2));
        assertEquals(TimCoordinateConverter.PathLengthExtension.NONE,
                TimCoordinateConverter.pathLengthExtensionFor(1, 0, 2));
        assertEquals(TimCoordinateConverter.PathLengthExtension.END,
                TimCoordinateConverter.pathLengthExtensionFor(2, 0, 2));
        assertEquals(TimCoordinateConverter.PathLengthExtension.NONE,
                TimCoordinateConverter.pathLengthExtensionFor(0, -1, -1));
    }

    @Test
    public void multiRegionPathLengthOffsetExtendsOnlyFirstAndLastRegions() throws Exception {
        List<TravelerDataFrame> dataFrames = loadDataFrames("ThreeRegionEastWestPathTIM.json");
        double lengthOffsetMeters = 300.0;
        TimCoordinateConverter.TimGeometry withoutOffset = convertOpenPath(dataFrames, 0.0, 0.0);
        TimCoordinateConverter.TimGeometry withOffset = convertOpenPath(dataFrames, 0.0, lengthOffsetMeters);

        assertEquals(6, withoutOffset.pathGeometry.getNumPoints());

        Coordinate firstStart = withoutOffset.pathGeometry.getCoordinateN(0);
        Coordinate firstEnd = withoutOffset.pathGeometry.getCoordinateN(1);
        Coordinate middleStart = withoutOffset.pathGeometry.getCoordinateN(2);
        Coordinate middleEnd = withoutOffset.pathGeometry.getCoordinateN(3);
        Coordinate lastEnd = withoutOffset.pathGeometry.getCoordinateN(5);

        Point westOfFirst = destinationPoint(firstStart, 270, 100);
        Point eastOfLast = destinationPoint(lastEnd, 90, 100);
        Point westOfMiddle = destinationPoint(middleStart, 270, 100);
        Point eastOfMiddle = destinationPoint(middleEnd, 90, 100);
        Point eastOfFirstInner = destinationPoint(firstEnd, 90, 100);

        assertFalse(covers(withoutOffset.geofenceGeometry, westOfFirst));
        assertFalse(covers(withoutOffset.geofenceGeometry, eastOfLast));
        assertTrue(covers(withOffset.geofenceGeometry, westOfFirst),
                "First region start should be length-extended");
        assertTrue(covers(withOffset.geofenceGeometry, eastOfLast),
                "Last region end should be length-extended");
        assertFalse(covers(withOffset.geofenceGeometry, westOfMiddle),
                "Middle region start should not be length-extended");
        assertFalse(covers(withOffset.geofenceGeometry, eastOfMiddle),
                "Middle region end should not be length-extended");
        assertFalse(covers(withOffset.geofenceGeometry, eastOfFirstInner),
                "Inner end of the first region should not be length-extended");
    }

    private List<TravelerDataFrame> loadDataFrames(String resourceName) throws Exception {
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream("/usdot/v2x/app/api/utils/" + resourceName));
        TravelerInformationMessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(),
                TravelerInformationMessageFrame.class);
        return messageFrame.getValue().getDataFrames();
    }

    private static TimCoordinateConverter.TimGeometry convertOpenPath(List<TravelerDataFrame> dataFrames,
            double geofenceOffsetMeters, double pathLengthOffsetMeters) {
        return convertOpenPath(dataFrames, geofenceOffsetMeters, pathLengthOffsetMeters, 0.0);
    }

    private static TimCoordinateConverter.TimGeometry convertOpenPath(List<TravelerDataFrame> dataFrames,
            double geofenceOffsetMeters, double pathLengthOffsetMeters, double tipWidthMeters) {
        GeometryProperties properties = new GeometryProperties();
        properties.setGeofenceOffsetMeters(geofenceOffsetMeters);
        properties.setGeofencePathLengthOffsetMeters(pathLengthOffsetMeters);
        properties.setGeofencePathExtensionTipWidthMeters(tipWidthMeters);
        return new TimCoordinateConverter(properties).convertTimToCoordinates(dataFrames);
    }

    private static Point destinationPoint(Coordinate lonLat, double bearingDegrees, double distanceMeters) {
        Point2D destination = GeometryUtils.calculateDestinationPoint(lonLat.x, lonLat.y, bearingDegrees,
                distanceMeters);
        return GeometryUtils.getGeometryFactory()
                .createPoint(new Coordinate(destination.getX(), destination.getY()));
    }

    private static Coordinate midpoint(Coordinate start, Coordinate end) {
        return new Coordinate((start.x + end.x) / 2.0, (start.y + end.y) / 2.0);
    }

    /**
     * Point {@code distanceMeters} beyond {@code endpoint}, continuing the direction
     * from {@code neighbor} to {@code endpoint}. Coordinates are (lon, lat).
     */
    private static Point beyondEndpoint(Coordinate neighbor, Coordinate endpoint, double distanceMeters) {
        double bearingDegrees = GeometryUtils.calculateBearing(neighbor.x, neighbor.y, endpoint.x, endpoint.y);
        return destinationPoint(endpoint, bearingDegrees, distanceMeters);
    }

    /**
     * Square-cap corridor built the same way the converter buffers an open path.
     * Input coordinates are (lon, lat), as in {@code pathGeometry}.
     */
    private static Geometry plainSquareCapCorridor(Coordinate[] lonLatPath, double halfWidthMeters)
            throws Exception {
        Coordinate[] latLonPath = new Coordinate[lonLatPath.length];
        for (int i = 0; i < lonLatPath.length; i++) {
            latLonPath[i] = new Coordinate(lonLatPath[i].y, lonLatPath[i].x);
        }
        LineString line = GeometryUtils.getGeometryFactory().createLineString(latLonPath);
        return GeometryUtils.bufferGeometryWithUtm(line, halfWidthMeters,
                GeometryUtils.createBufferParameters(1, BufferParameters.CAP_SQUARE));
    }

    private static boolean covers(Geometry geofence, Point point) {
        return geofence.covers(point) || DistanceOp.distance(point, geofence) == 0.0;
    }

    private static double lonDeltaToMeters(double lonDelta, double latitude) {
        return lonDelta * 111111.0 * Math.cos(Math.toRadians(latitude));
    }

    private static double latDeltaToMeters(double latDelta) {
        return latDelta * 111111.0;
    }
}
