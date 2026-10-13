package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.distance.DistanceOp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import j2735ffm.MessageFrameCodec;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import usdot.v2x.app.api.config.GeometryProperties;

/**
 * Regenerates geofences from the sample active deployment exports and verifies
 * triangular path extensions using production-like geometry settings.
 */
class ActiveTimDeploymentTriangularExtensionTest {

    private static final double GEOFENCE_OFFSET_METERS = 100.0;
    private static final double PATH_LENGTH_OFFSET_METERS = 200.0;
    private static final double TIP_WIDTH_METERS = 0.0;

    private static MessageFrameCodec codec;
    private static XmlMapper xmlMapper;
    private static ObjectMapper objectMapper;
    private static TimCoordinateConverter converter;

    @BeforeAll
    static void setUpCodec() {
        objectMapper = new ObjectMapper();
        xmlMapper = new XmlMapper();

        GeometryProperties properties = new GeometryProperties();
        properties.setGeofenceOffsetMeters(GEOFENCE_OFFSET_METERS);
        properties.setGeofencePathLengthOffsetMeters(PATH_LENGTH_OFFSET_METERS);
        properties.setGeofencePathExtensionTipWidthMeters(TIP_WIDTH_METERS);
        converter = new TimCoordinateConverter(properties);

        codec = tryCreateCodec();
        assumeTrue(codec != null, "MessageFrameCodec unavailable; skipping deployment export validation");
    }

    static Stream<String> deploymentResourcePaths() {
        return Stream.of(
                "../resources/active-tim-deployments.json",
                "../resources/active-tim-deployments 1.json");
    }

    @ParameterizedTest
    @MethodSource("deploymentResourcePaths")
    void activeTimDeploymentsProduceTriangularExtensions(String resourcePath) throws Exception {
        Path filePath = Path.of(resourcePath).normalize().toAbsolutePath();
        assumeTrue(Files.exists(filePath), "Deployment export not found: " + filePath);

        JsonNode deployments = objectMapper.readTree(filePath.toFile());
        int timDeployments = 0;
        int validated = 0;

        for (JsonNode deployment : deployments) {
            String hexPayload = deployment.path("hex_payload").asText(null);
            if (hexPayload == null || hexPayload.isBlank()) {
                continue;
            }

            MessageFrame<?> messageFrame = decodeMessageFrame(hexPayload);
            if (!(messageFrame instanceof TravelerInformationMessageFrame timFrame)) {
                continue;
            }

            timDeployments++;
            List<TravelerDataFrame> dataFrames = timFrame.getValue().getDataFrames();
            assumeTrue(dataFrames != null && !dataFrames.isEmpty());

            TimCoordinateConverter.TimGeometry triangular = converter.convertTimToCoordinates(dataFrames);
            TimCoordinateConverter.TimGeometry rectangular = convertWithLegacyRectangularExtensions(dataFrames);

            assertNotNull(triangular.geofenceGeometry);
            assertTrue(triangular.geofenceGeometry.getArea() > 0);
            assertTrue(triangular.geofenceGeometry.getArea() <= rectangular.geofenceGeometry.getArea() + 1.0,
                    "Triangular extensions should not exceed legacy rectangular area for "
                            + deployment.path("geofence_id").asText());

            if (hasOpenPath(dataFrames)) {
                assertTriangularTipIsNarrower(triangular, dataFrames);
                validated++;
            }
        }

        assumeTrue(timDeployments > 0, "Expected TIM deployments in " + filePath);
        assumeTrue(validated > 0, "Expected at least one open-path TIM in " + filePath);
    }

    private static void assertTriangularTipIsNarrower(TimCoordinateConverter.TimGeometry geometry,
            List<TravelerDataFrame> dataFrames) {
        Coordinate start = geometry.pathGeometry.getCoordinateN(0);
        Coordinate end = geometry.pathGeometry.getCoordinateN(geometry.pathGeometry.getNumPoints() - 1);
        Coordinate next = geometry.pathGeometry.getNumPoints() > 1
                ? geometry.pathGeometry.getCoordinateN(1)
                : start;
        Coordinate prev = geometry.pathGeometry.getNumPoints() > 1
                ? geometry.pathGeometry.getCoordinateN(geometry.pathGeometry.getNumPoints() - 2)
                : end;

        double startBearing = GeometryUtils.calculateBearing(next.x, next.y, start.x, start.y);
        double endBearing = GeometryUtils.calculateBearing(prev.x, prev.y, end.x, end.y);

        Point startTip = destinationPoint(start, startBearing, PATH_LENGTH_OFFSET_METERS * 0.8);
        Point endTip = destinationPoint(end, endBearing, PATH_LENGTH_OFFSET_METERS * 0.8);

        double lateralOffsetMeters = GEOFENCE_OFFSET_METERS + 20.0;
        Point startWide = offsetPerpendicular(startTip, startBearing, lateralOffsetMeters);
        Point endWide = offsetPerpendicular(endTip, endBearing, lateralOffsetMeters);

        assertTrue(covers(geometry.geofenceGeometry, startTip),
                "Start extension tip should remain covered");
        assertTrue(covers(geometry.geofenceGeometry, endTip),
                "End extension tip should remain covered");
        assertFalse(covers(geometry.geofenceGeometry, startWide),
                "Start extension should taper away from full corridor width");
        assertFalse(covers(geometry.geofenceGeometry, endWide),
                "End extension should taper away from full corridor width");
    }

    private static TimCoordinateConverter.TimGeometry convertWithLegacyRectangularExtensions(
            List<TravelerDataFrame> dataFrames) {
        GeometryProperties properties = new GeometryProperties();
        properties.setGeofenceOffsetMeters(GEOFENCE_OFFSET_METERS);
        properties.setGeofencePathLengthOffsetMeters(PATH_LENGTH_OFFSET_METERS);
        properties.setGeofencePathExtensionTipWidthMeters(2 * (GEOFENCE_OFFSET_METERS + 7.5));
        return new TimCoordinateConverter(properties).convertTimToCoordinates(dataFrames);
    }

    private static boolean hasOpenPath(List<TravelerDataFrame> dataFrames) {
        for (TravelerDataFrame dataFrame : dataFrames) {
            if (dataFrame.getRegions() == null) {
                continue;
            }
            for (var region : dataFrame.getRegions()) {
                boolean closedPath = region.getClosedPath() != null && region.getClosedPath().getValue();
                boolean closedRegion = region.getDescription() != null
                        && region.getDescription().getGeometry() != null
                        && region.getDescription().getGeometry().getCircle() != null;
                if (!closedPath && !closedRegion) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MessageFrame<?> decodeMessageFrame(String hexPayload) throws Exception {
        String trimmedHex = UperUtil.trimToMessagePayload(hexPayload);
        byte[] bytes = HexFormat.of().parseHex(trimmedHex);
        String xer = codec.uperToXer(bytes);
        return xmlMapper.readValue(xer, MessageFrame.class);
    }

    private static MessageFrameCodec tryCreateCodec() {
        Path projectRoot = Path.of(System.getProperty("user.dir")).normalize();
        Path[] candidates = {
                projectRoot.resolve("../j2735-ffm-java/lib/libasnapplication.so"),
                projectRoot.resolve("../j2735-ffm-java/j2735-2024-ffm-lib/src/test/resources/j2735ffm/libasnapplication.so"),
                Path.of("/usr/lib/libasnapplication.so")
        };

        for (Path candidate : candidates) {
            Path libPath = candidate.normalize().toAbsolutePath();
            if (!Files.exists(libPath)) {
                continue;
            }
            try {
                return new MessageFrameCodec(262144L, 8192L, 256L, libPath);
            } catch (RuntimeException ignored) {
                // try next candidate
            }
        }

        return null;
    }

    private static Point destinationPoint(Coordinate lonLat, double bearingDegrees, double distanceMeters) {
        Point2D destination = GeometryUtils.calculateDestinationPoint(lonLat.x, lonLat.y, bearingDegrees,
                distanceMeters);
        return GeometryUtils.getGeometryFactory()
                .createPoint(new Coordinate(destination.getX(), destination.getY()));
    }

    private static Point offsetPerpendicular(Point tip, double pathBearingDegrees, double distanceMeters) {
        Point2D destination = GeometryUtils.calculateDestinationPoint(
                tip.getX(), tip.getY(), pathBearingDegrees + 90.0, distanceMeters);
        return GeometryUtils.getGeometryFactory()
                .createPoint(new Coordinate(destination.getX(), destination.getY()));
    }

    private static boolean covers(Geometry geofence, Point point) {
        Envelope envelope = geofence.getEnvelopeInternal();
        if (!envelope.contains(point.getCoordinate())) {
            return false;
        }
        return geofence.covers(point) || DistanceOp.distance(point, geofence) == 0.0;
    }
}
