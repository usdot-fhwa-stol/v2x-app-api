package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import j2735ffm.MessageFrameCodec;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.UniqueMSGID;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.models.etx.configuration.DepositRequest;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.etx.configuration.geometry.Polygon;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentRequest;
import usdot.v2x.app.api.models.geofence.GeohashPreviewResponse;
import usdot.v2x.app.api.services.ErrorLoggingService;
import usdot.v2x.app.api.services.MessageTypeService;

class GeofenceDeploymentConverterOverrideThresholdTest {

    private static final double[][] LARGE_OVERRIDE_RING = {
            {-83.27, 42.26},
            {-83.27, 42.28},
            {-83.25, 42.28},
            {-83.25, 42.26},
            {-83.27, 42.26}
    };

    private static final Coordinate[] TINY_GENERATED_COORDS = {
            new Coordinate(-83.0470, 42.3280),
            new Coordinate(-83.0470, 42.3282),
            new Coordinate(-83.0468, 42.3282),
            new Coordinate(-83.0468, 42.3280),
            new Coordinate(-83.0470, 42.3280)
    };

    private GeofenceProperties properties;
    private GeohashUtils geohashUtils;
    private GeofenceDeploymentConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        properties = new GeofenceProperties();
        properties.getGeohash().setPrecision(7);
        properties.getLimits().setMinGeohashes(1);
        properties.getLimits().setMaxGeohashes(500);
        properties.getLimits().setMaxCandidateCells(100_000);
        properties.getLimits().setAllowOverlappingGeohashes(true);

        geohashUtils = new GeohashUtils(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(ErrorLoggingService.class),
                properties,
                mock(JdbcTemplate.class));

        TravelerInformation tim = new TravelerInformation();
        tim.setPacketID(new UniqueMSGID("000000000000000001"));
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        dataFrames.add(new TravelerDataFrame());
        tim.setDataFrames(dataFrames);
        TravelerInformationMessageFrame frame = new TravelerInformationMessageFrame();
        frame.setValue(tim);

        XmlMapper xmlMapper = mock(XmlMapper.class);
        when(xmlMapper.readValue(anyString(), eq(MessageFrame.class))).thenReturn(frame);

        MessageFrameCodec codec = mock(MessageFrameCodec.class);
        when(codec.uperToXer(any(byte[].class))).thenReturn("<MessageFrame/>");

        GeometryFactory geometryFactory = new GeometryFactory();
        org.locationtech.jts.geom.Polygon tinyPolygon = geometryFactory.createPolygon(TINY_GENERATED_COORDS);

        TimCoordinateConverter timCoordinateConverter = mock(TimCoordinateConverter.class);
        when(timCoordinateConverter.convertTimToCoordinates(any()))
                .thenReturn(new TimCoordinateConverter.TimGeometry(
                        geometryFactory.createLineString(new Coordinate[] {
                                TINY_GENERATED_COORDS[0], TINY_GENERATED_COORDS[1]
                        }), tinyPolygon));

        MessageTypeService messageTypeService = mock(MessageTypeService.class);
        when(messageTypeService.resolveCodeByAsnClass(anyString())).thenReturn("TIM");

        converter = new GeofenceDeploymentConverter(
                xmlMapper,
                geohashUtils,
                codec,
                null,
                messageTypeService,
                timCoordinateConverter,
                null,
                properties,
                new OverrideGeofenceResolver(geohashUtils, properties));
    }

    @Test
    void convertReplacesOversizedOverrideWithMessageGeofence() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        int overrideCount = geohashCount(override);
        assertTrue(overrideCount > 1, "Override fixture must produce multiple geohashes, got " + overrideCount);
        properties.getLimits().setOverrideGeohashThreshold(overrideCount - 1);

        DepositRequest request = deposit(override);
        GeofenceDeploymentRequest converted = converter.convertToGeofenceDeploymentRequest(request);

        assertEquals(-83.0470, firstLongitude(converted.getGeojson()), 0.0001,
                "Oversized override should be replaced with the TIM-generated geofence");
        assertNotEquals(-83.27, firstLongitude(converted.getGeojson()), 0.0001);
        assertTrue(converted.getGeohashes().size() < overrideCount,
                "Generated geofence should cover fewer geohashes than the oversized override");
    }

    @Test
    void convertKeepsOverrideWhenUnderThreshold() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        int filteredCount = geohashCount(override);
        int unfilteredCount = unfilteredGeohashCount(override);
        assertTrue(unfilteredCount > filteredCount,
                "Override fixture must be denser without 3×3 filtering, filtered="
                        + filteredCount + " unfiltered=" + unfilteredCount);
        properties.getLimits().setOverrideGeohashThreshold(filteredCount);

        DepositRequest request = deposit(override);
        GeofenceDeploymentRequest converted = converter.convertToGeofenceDeploymentRequest(request);

        assertEquals(-83.27, firstLongitude(converted.getGeojson()), 0.0001,
                "Override at the threshold should be forwarded as the deployment geofence");
        assertEquals(unfilteredCount, converted.getGeohashes().size());
    }

    @Test
    void previewKeepsOverrideWithoutRepresentativeFiltering() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        int filteredCount = geohashCount(override);
        int unfilteredCount = unfilteredGeohashCount(override);
        properties.getLimits().setOverrideGeohashThreshold(filteredCount);

        GeohashPreviewResponse preview = converter.previewGeohashes(deposit(override), false);

        assertEquals(-83.27, firstLongitude(preview.getGeofence()), 0.0001);
        assertEquals(unfilteredCount, preview.getGeohashCount());
    }

    @Test
    void previewReplacesOversizedOverrideWithMessageGeofence() {
        GeofenceFeatureCollection override = polygon(LARGE_OVERRIDE_RING);
        int overrideCount = geohashCount(override);
        properties.getLimits().setOverrideGeohashThreshold(Math.max(1, overrideCount - 1));

        GeohashPreviewResponse preview = converter.previewGeohashes(deposit(override), false);

        assertEquals(-83.0470, firstLongitude(preview.getGeofence()), 0.0001);
        assertTrue(preview.getGeohashCount() < overrideCount);
    }

    private DepositRequest deposit(GeofenceFeatureCollection override) {
        DepositRequest request = new DepositRequest();
        request.setAsn1Hex("001F00");
        request.setOverrideGeofence(override);
        return request;
    }

    private int geohashCount(GeofenceFeatureCollection geofence) {
        return geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                geofence, properties.getGeohash().getPrecision(), Set.of()).size();
    }

    private int unfilteredGeohashCount(GeofenceFeatureCollection geofence) {
        return geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                geofence, properties.getGeohash().getPrecision(), Set.of(), false).size();
    }

    private static double firstLongitude(GeofenceFeatureCollection geofence) {
        Polygon polygon = (Polygon) geofence.getFeatures().getFirst().getGeometry();
        return polygon.getCoordinates().getFirst().getFirst().getFirst();
    }

    private static GeofenceFeatureCollection polygon(double[][] ring) {
        List<List<Double>> coordinates = new ArrayList<>();
        for (double[] point : ring) {
            coordinates.add(List.of(point[0], point[1]));
        }

        Polygon geometry = new Polygon();
        geometry.setCoordinates(List.of(coordinates));

        GeofenceFeature feature = new GeofenceFeature();
        feature.setType("Feature");
        feature.setGeometry(geometry);
        feature.setProperties(Map.of());

        GeofenceFeatureCollection collection = new GeofenceFeatureCollection();
        collection.setType("FeatureCollection");
        collection.setFeatures(List.of(feature));
        return collection;
    }
}
