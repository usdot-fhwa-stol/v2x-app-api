package usdot.v2x.app.api.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.configuration.DepositRequest;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentRequest;
import j2735ffm.MessageFrameCodec;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import usdot.v2x.app.api.exceptions.NoAvailableGeohashException;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.MapData.MapDataMessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.MapData.IntersectionGeometryList;

import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;

import usdot.v2x.app.api.services.MessageTypeService;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import usdot.v2x.app.api.utils.GeofenceFeatureHelper;

@Component
@Slf4j
public class GeofenceDeploymentConverter {

    private final XmlMapper xmlMapper;
    private final GeohashUtils geohashUtils;
    private final MessageFrameCodec codec;
    private final TimExpirationCalculator timExpirationCalculator;
    private final MessageTypeService messageTypeService;
    private final TimCoordinateConverter timCoordinateConverter;
    private final MapCoordinateConverter mapCoordinateConverter;

    @Value("${tim.expiration.grace-period-hours:2}")
    private int gracePeriodHours;

    public GeofenceDeploymentConverter(@Qualifier("xmlMapper") XmlMapper xmlMapper,
            GeohashUtils geohashUtils,
            @Autowired(required = false) MessageFrameCodec codec,
            @Autowired(required = false) TimExpirationCalculator timExpirationCalculator,
            MessageTypeService messageTypeService,
            @Autowired(required = false) TimCoordinateConverter timCoordinateConverter,
            @Autowired(required = false) MapCoordinateConverter mapCoordinateConverter) {
        this.xmlMapper = xmlMapper;
        this.geohashUtils = geohashUtils;
        this.codec = codec;
        this.timExpirationCalculator = timExpirationCalculator;
        this.messageTypeService = messageTypeService;
        this.timCoordinateConverter = timCoordinateConverter;
        this.mapCoordinateConverter = mapCoordinateConverter;
    }

    /**
     * Convert ConfigurationDepositRequest to GeofenceDeploymentRequest
     * 
     * @param configRequest The configuration deposit request
     * @return GeofenceDeploymentRequest
     */
    public GeofenceDeploymentRequest convertToGeofenceDeploymentRequest(DepositRequest configRequest) {
        GeofenceDeploymentRequest geofenceRequest = new GeofenceDeploymentRequest();

        // Parse the ASN.1 message to determine message type and generate appropriate ID
        MessageFrame<?> messageFrame;
        try {
            messageFrame = parseMessageFrame(configRequest.getAsn1Hex());
        } catch (JsonProcessingException e) {
            log.error("Error parsing ASN.1 message for Geofence ID generation: {}", e.getMessage(), e);
            throw new ErrorResponseException(
                    new ErrorResponse("Failed to parse ASN.1 message", e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String geofenceId;
        if (messageFrame == null) {
            log.warn("Could not parse ASN.1 message, using fallback ID generation");
            geofenceId = generateFallbackGeofenceId(configRequest.getAsn1Hex());
        } else {
            // Check message type and generate ID accordingly
            geofenceId = generateGeofenceIdByMessageType(messageFrame, configRequest.getAsn1Hex());
        }
        geofenceRequest.setGeofenceId(geofenceId);

        // Set the hex payload
        geofenceRequest.setHexPayload(configRequest.getAsn1Hex());

        // Resolve message type code from database using ASN class name
        if (messageFrame == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("Failed to detect message type", "ASN.1 parsing returned null frame"),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String asnClassName = messageFrame.getClass().getName();
        String msgTypeCode = messageTypeService.resolveCodeByAsnClass(asnClassName);
        geofenceRequest.setMsgType(msgTypeCode);

        // Get the current user from security context
        String deployedBy = getCurrentUsername();
        geofenceRequest.setDeployedBy(deployedBy);

        try {
            Instant expirationTime = null;
            if (timExpirationCalculator != null) {
                expirationTime = timExpirationCalculator.calculateExpirationTime(
                        configRequest.getAsn1Hex(), gracePeriodHours);
            }

            if (expirationTime != null) {
                geofenceRequest.setExpiresAt(expirationTime.toString());
                log.debug("Calculated expiration time for Geofence {}: {}", geofenceId, expirationTime);
            } else {
                log.warn("Could not calculate expiration time for Geofence {}, will not set expiration",
                        geofenceId);
            }
        } catch (Exception e) {
            log.error("Error calculating expiration time for Geofence {}: {}", geofenceId, e.getMessage(), e);
            // Continue without setting expiration time
        }

        // Handle geofence data
        GeofenceFeatureCollection geofence = configRequest.getOverrideGeofence();
        if (geofence != null) {
            // Use the override geofence directly
            geofenceRequest.setGeojson(geofence);

            try {
                List<String> geohashes = geohashUtils.extractGeohashesFromGeofenceFeatureCollection(geofence,
                        geofenceId);
                geofenceRequest.setGeohashes(geohashes);
            } catch (NoAvailableGeohashException ex) {
                throw new ErrorResponseException(
                        new ErrorResponse("NO_AVAILABLE_GEOHASH", ex.getMessage()),
                        HttpStatus.CONFLICT);
            } catch (RuntimeException ex) {
                throw ex;
            }
        } else {
            // Extract geofence from the ASN.1 message if no override is provided
            log.debug("No override geofence provided, extracting geofence from ASN.1 message");
            geofence = extractGeofenceFromMessage(messageFrame);
            geofenceRequest.setGeojson(geofence);

            try {
                List<String> geohashes = geohashUtils.extractGeohashesFromGeofenceFeatureCollection(geofence,
                        geofenceId);
                geofenceRequest.setGeohashes(geohashes);
            } catch (NoAvailableGeohashException ex) {
                throw new ErrorResponseException(
                        new ErrorResponse("NO_AVAILABLE_GEOHASH", ex.getMessage()),
                        HttpStatus.CONFLICT);
            } catch (RuntimeException ex) {
                throw ex;
            }
        }

        return geofenceRequest;
    }

    /**
     * Generate a unique Geofence ID based on message type
     * 
     * @param messageFrame The parsed message frame
     * @param hexPayload   The hex payload
     * @return A unique Geofence ID
     */
    private String generateGeofenceIdByMessageType(MessageFrame<?> messageFrame, String hexPayload) {
        if (messageFrame instanceof TravelerInformationMessageFrame) {
            // Handle TIM messages
            return generateTimGeofenceId((TravelerInformationMessageFrame) messageFrame);
        } else if (messageFrame instanceof MapDataMessageFrame) {
            // Handle MAP messages
            return generateMapGeofenceId((MapDataMessageFrame) messageFrame);
        } else {
            log.error("Unsupported message type: {}. Only TIM and MAP messages are currently supported.",
                    messageFrame.getClass().getSimpleName());
            throw new ErrorResponseException(
                    new ErrorResponse("UNSUPPORTED_MESSAGE_TYPE",
                            "Message type " + messageFrame.getClass().getSimpleName()
                                    + " is not supported. Only TIM and MAP messages are currently supported."),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Generate a unique Geofence ID for TIM messages based on packet ID and msgCnt
     * value
     * 
     * @param timFrame The TIM message frame
     * @return A unique Geofence ID
     */
    private String generateTimGeofenceId(TravelerInformationMessageFrame timFrame) {
        try {
            TravelerInformation tim = timFrame.getValue();

            // Extract packet ID
            String packetIdHex = tim.getPacketID().getValue();

            // Extract msgCnt value
            String msgCnt = "0"; // Default value
            if (tim.getMsgCnt() != null) {
                msgCnt = String.valueOf(tim.getMsgCnt().getValue());
            }

            // Create Geofence ID with packet ID and msgCnt (no prefix)
            return packetIdHex + "_" + msgCnt;
        } catch (Exception e) {
            log.error("Error generating TIM Geofence ID: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate TIM Geofence ID", e);
        }
    }

    /**
     * Generate a unique Geofence ID for MAP messages based on intersection ID
     * 
     * @param mapFrame The MAP message frame
     * @return A unique Geofence ID
     */
    private String generateMapGeofenceId(MapDataMessageFrame mapFrame) {
        try {
            us.dot.its.jpo.asn.j2735.r2024.MapData.MapData mapData = mapFrame.getValue();
            if (mapData.getIntersections() == null || mapData.getIntersections().isEmpty()) {
                throw new ErrorResponseException(
                        new ErrorResponse("MAP message must contain at least one intersection", "invalid MAP message"),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            // Use the first intersection ID
            String intersectionId = String.valueOf(mapData.getIntersections().getFirst().getId().getId().getValue());
            return intersectionId;
        } catch (ErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating MAP Geofence ID: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate MAP Geofence ID", e);
        }
    }

    /**
     * Parse ASN1 hex string to MessageFrame (similar to ConfigurationApi)
     */
    private MessageFrame<?> parseMessageFrame(String asn1Hex) throws JsonProcessingException {
        if (codec == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("CODEC_UNAVAILABLE",
                            "MessageFrameCodec is not available. Codec is disabled for OpenAPI generation."),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            // Trim the hex string to remove any headers and get just the message payload
            String trimmedHex = UperUtil.trimToMessagePayload(asn1Hex);
            byte[] bytes = HexFormat.of().parseHex(trimmedHex);
            String xer = codec.uperToXer(bytes);
            return xmlMapper.readValue(xer, MessageFrame.class);
        } catch (Exception e) {
            log.error("Failed to parse ASN1 hex", e);
            throw new ErrorResponseException(
                    new ErrorResponse("Failed to parse ASN1 hex", e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Generate a fallback Geofence ID when ASN.1 parsing fails
     * 
     * @param hexPayload The hex payload
     * @return A fallback Geofence ID
     */
    private String generateFallbackGeofenceId(String hexPayload) {
        // Generate a simple hash-based ID from the hex payload
        int hash = hexPayload.hashCode();
        return "GEOFENCE_" + Math.abs(hash) + "_" + System.currentTimeMillis();
    }

    /**
     * Extract geofence from ASN.1 message frame
     * 
     * @param messageFrame The parsed message frame
     * @return GeofenceFeatureCollection extracted from the message
     */
    private GeofenceFeatureCollection extractGeofenceFromMessage(MessageFrame<?> messageFrame) {
        if (messageFrame instanceof TravelerInformationMessageFrame) {
            return extractGeofenceFromTim((TravelerInformationMessageFrame) messageFrame);
        } else if (messageFrame instanceof MapDataMessageFrame) {
            return extractGeofenceFromMap((MapDataMessageFrame) messageFrame);
        } else {
            throw new ErrorResponseException(
                    new ErrorResponse("UNSUPPORTED_MESSAGE_TYPE",
                            "Message type " + messageFrame.getClass().getSimpleName()
                                    + " is not supported. Only TIM and MAP messages are currently supported."),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Extract geofence from TIM message
     * 
     * @param timFrame The TIM message frame
     * @return GeofenceFeatureCollection extracted from the TIM message
     */
    private GeofenceFeatureCollection extractGeofenceFromTim(TravelerInformationMessageFrame timFrame) {
        if (timCoordinateConverter == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("TIM_CONVERTER_UNAVAILABLE",
                            "TimCoordinateConverter is not available. Cannot extract geofence from TIM message."),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            TravelerInformation tim = timFrame.getValue();
            List<us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame> dataFrames = tim.getDataFrames();
            if (dataFrames == null || dataFrames.isEmpty()) {
                throw new ErrorResponseException(
                        new ErrorResponse("TIM message must contain at least one data frame", "invalid TIM message"),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            TimCoordinateConverter.TimGeometry geometry = timCoordinateConverter.convertTimToCoordinates(dataFrames);
            return convertPolygonToGeofenceFeatureCollection(geometry.geofenceGeometry);
        } catch (ErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting geofence from TIM message: {}", e.getMessage(), e);
            throw new ErrorResponseException(
                    new ErrorResponse("FAILED_TO_EXTRACT_GEOFENCE",
                            "Failed to extract geofence from TIM message: " + e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Extract geofence from MAP message
     * 
     * @param mapFrame The MAP message frame
     * @return GeofenceFeatureCollection extracted from the MAP message
     */
    private GeofenceFeatureCollection extractGeofenceFromMap(MapDataMessageFrame mapFrame) {
        if (mapCoordinateConverter == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("MAP_CONVERTER_UNAVAILABLE",
                            "MapCoordinateConverter is not available. Cannot extract geofence from MAP message."),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            us.dot.its.jpo.asn.j2735.r2024.MapData.MapData mapData = mapFrame.getValue();
            IntersectionGeometryList intersections = mapData.getIntersections();
            if (intersections == null || intersections.isEmpty()) {
                throw new ErrorResponseException(
                        new ErrorResponse("MAP message must contain at least one intersection", "invalid MAP message"),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            MapCoordinateConverter.MapGeometry geometry = mapCoordinateConverter.convertMapToCoordinates(intersections);
            return convertPolygonToGeofenceFeatureCollection(geometry.geofenceGeometry);
        } catch (ErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting geofence from MAP message: {}", e.getMessage(), e);
            throw new ErrorResponseException(
                    new ErrorResponse("FAILED_TO_EXTRACT_GEOFENCE",
                            "Failed to extract geofence from MAP message: " + e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Convert JTS Polygon to GeofenceFeatureCollection
     * 
     * @param polygon The JTS Polygon to convert
     * @return GeofenceFeatureCollection containing the polygon
     */
    private GeofenceFeatureCollection convertPolygonToGeofenceFeatureCollection(Polygon polygon) {
        GeofenceFeature feature = new GeofenceFeature();
        feature.setType("Feature");
        feature.setProperties(new HashMap<>());

        // Use the helper to convert JTS Polygon to custom geometry
        GeofenceFeatureHelper.setGeometry(feature, polygon);

        GeofenceFeatureCollection geoFence = new GeofenceFeatureCollection();
        geoFence.setType("FeatureCollection");
        geoFence.setFeatures(List.of(feature));

        return geoFence;
    }

    /**
     * Get the current username from the security context
     * 
     * @return The current username or "system" if not available
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Could not get current username from security context: {}", e.getMessage());
        }
        return "system";
    }
}
