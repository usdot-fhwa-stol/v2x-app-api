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

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import usdot.v2x.app.api.services.MessageTypeService;

@Component
@Slf4j
public class GeofenceDeploymentConverter {

    private final XmlMapper xmlMapper;
    private final GeohashUtils geohashUtils;
    private final MessageFrameCodec codec;
    private final TimExpirationCalculator timExpirationCalculator;
    private final MessageTypeService messageTypeService;

    @Value("${tim.expiration.grace-period-hours:2}")
    private int gracePeriodHours;

    public GeofenceDeploymentConverter(@Qualifier("xmlMapper") XmlMapper xmlMapper,
            GeohashUtils geohashUtils, MessageFrameCodec codec, TimExpirationCalculator timExpirationCalculator,
            MessageTypeService messageTypeService) {
        this.xmlMapper = xmlMapper;
        this.geohashUtils = geohashUtils;
        this.codec = codec;
        this.timExpirationCalculator = timExpirationCalculator;
        this.messageTypeService = messageTypeService;
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
            Instant expirationTime = timExpirationCalculator.calculateExpirationTime(
                    configRequest.getAsn1Hex(), gracePeriodHours);

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
            // TODO: Extract geofence from the ASN.1 message if no override is provided
            // This would require parsing the V2X message to extract geographical
            // information
            log.warn("No override geofence provided and automatic extraction not implemented yet");
            throw new UnsupportedOperationException(
                    "Automatic geofence extraction from ASN.1 message not yet implemented. Please provide override_geofence.");
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
        } else {
            // For now, only TIM messages are supported
            log.error("Unsupported message type: {}. Only TIM messages are currently supported.",
                    messageFrame.getClass().getSimpleName());
            throw new UnsupportedOperationException(
                    "Message type " + messageFrame.getClass().getSimpleName()
                            + " is not supported. Only TIM messages are currently supported.");
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
     * Parse ASN1 hex string to MessageFrame (similar to ConfigurationApi)
     */
    private MessageFrame<?> parseMessageFrame(String asn1Hex) throws JsonProcessingException {
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
