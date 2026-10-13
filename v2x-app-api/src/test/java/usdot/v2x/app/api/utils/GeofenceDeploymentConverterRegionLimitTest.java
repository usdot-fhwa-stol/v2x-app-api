package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.http.HttpStatus;
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
import usdot.v2x.app.api.exceptions.GeofenceTooLargeException;
import usdot.v2x.app.api.models.etx.configuration.DepositRequest;
import usdot.v2x.app.api.services.ErrorLoggingService;
import usdot.v2x.app.api.services.MessageTypeService;

class GeofenceDeploymentConverterRegionLimitTest {

    private static final double[][] OVERSIZED_TRIANGLE = {
            {-106.43815146211979, 40.71355603945426},
            {-105.33535432407251, 42.56947481806128},
            {-107.90265452163949, 42.41969732324517},
            {-106.43815146211979, 40.71355603945426}
    };

    private GeofenceDeploymentConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        GeofenceProperties properties = new GeofenceProperties();
        properties.getGeohash().setPrecision(7);
        properties.getLimits().setMaxCandidateCells(10_000);
        properties.getLimits().setMaxGeohashes(500);
        properties.getLimits().setMinGeohashes(1);
        properties.getLimits().setAllowOverlappingGeohashes(true);

        GeohashUtils geohashUtils = new GeohashUtils(
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
        Coordinate[] coordinates = new Coordinate[OVERSIZED_TRIANGLE.length];
        for (int i = 0; i < OVERSIZED_TRIANGLE.length; i++) {
            coordinates[i] = new Coordinate(OVERSIZED_TRIANGLE[i][0], OVERSIZED_TRIANGLE[i][1]);
        }
        org.locationtech.jts.geom.Polygon oversizedPolygon = geometryFactory.createPolygon(coordinates);

        TimCoordinateConverter timCoordinateConverter = mock(TimCoordinateConverter.class);
        when(timCoordinateConverter.convertTimToCoordinates(any()))
                .thenReturn(new TimCoordinateConverter.TimGeometry(
                        geometryFactory.createLineString(new Coordinate[0]), oversizedPolygon));

        MessageTypeService messageTypeService = mock(MessageTypeService.class);
        when(messageTypeService.resolveCodeByAsnClass(anyString())).thenReturn("TIM");

        converter = new GeofenceDeploymentConverter(
                xmlMapper,
                geohashUtils,
                codec,
                null,
                messageTypeService,
                timCoordinateConverter,
                null);
    }

    @Test
    void oversizedTimDerivedRegionIsRejectedByDepositConversion() {
        DepositRequest request = new DepositRequest();
        request.setAsn1Hex("001F00");
        request.setOverrideGeofence(null);

        GeofenceTooLargeException exception = assertThrows(GeofenceTooLargeException.class,
                () -> converter.convertToGeofenceDeploymentRequest(request));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
        assertEquals("GEOFENCE_TOO_LARGE", exception.getErrorResponse().getError());
    }
}
