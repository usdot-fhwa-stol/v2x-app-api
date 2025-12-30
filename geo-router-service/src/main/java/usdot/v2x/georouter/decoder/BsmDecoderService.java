package usdot.v2x.georouter.decoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import j2735ffm.MessageFrameCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.asn.j2735.r2024.BasicSafetyMessage.BasicSafetyMessage;
import us.dot.its.jpo.asn.j2735.r2024.Common.BSMcoreData;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import usdot.v2x.georouter.models.GeoRelevanceMessage;

import java.time.Instant;

/**
 * Service for decoding BSM (Basic Safety Message) messages from ASN.1 UPER format
 * and extracting geographic coordinates.
 */
@Slf4j
@Service
public class BsmDecoderService {

    private static final double J2735_DECIMAL_CONVERSION_FACTOR = 10000000.0;

    private final MessageFrameCodec codec;
    private final XmlMapper xmlMapper;

    public BsmDecoderService(MessageFrameCodec codec, XmlMapper xmlMapper) {
        this.codec = codec;
        this.xmlMapper = xmlMapper;
    }

    /**
     * Decodes a BSM message from UPER binary format and extracts geographic coordinates.
     * 
     * @param uperBytes ASN.1 UPER encoded message bytes
     * @param sourceTopic Original MQTT topic where message was received
     * @return GeoRelevanceMessage with extracted coordinates, or null if decoding fails
     */
    public GeoRelevanceMessage decodeBsm(byte[] uperBytes, String sourceTopic) {
        try {
            // Convert UPER to XER
            String xer = codec.uperToXer(uperBytes);
            log.debug("Decoded UPER to XER, length: {} bytes", xer.length());

            // Parse XER to MessageFrame
            MessageFrame<?> messageFrame = xmlMapper.readValue(xer, MessageFrame.class);
            
            // Check if this is a BSM message (messageId = 20)
            if (messageFrame.getMessageId() == null || messageFrame.getMessageId().getValue() != 20) {
                log.warn("Message is not a BSM (messageId: {}), skipping", messageFrame.getMessageId());
                return null;
            }

            // Extract BasicSafetyMessage from MessageFrame
            Object value = messageFrame.getValue();
            if (!(value instanceof BasicSafetyMessage)) {
                log.warn("MessageFrame value is not a BasicSafetyMessage, type: {}", 
                    value != null ? value.getClass().getName() : "null");
                return null;
            }

            BasicSafetyMessage bsm = (BasicSafetyMessage) value;
            BSMcoreData coreData = bsm.getCoreData();

            if (coreData == null) {
                log.warn("BSM does not contain coreData");
                return null;
            }

            // Extract latitude and longitude
            if (coreData.getLat() == null || coreData.getLong_() == null) {
                log.warn("BSM coreData missing lat/long");
                return null;
            }

            double latitude = coreData.getLat().getValue() / J2735_DECIMAL_CONVERSION_FACTOR;
            double longitude = coreData.getLong_().getValue() / J2735_DECIMAL_CONVERSION_FACTOR;
            
            // Extract device ID from BSM coreData (TemporaryID is an octet string)
            String deviceId = null;
            if (coreData.getId() != null) {
                // TemporaryID extends Asn1OctetString, getValue() returns hex string
                String idHex = coreData.getId().getValue();
                if (idHex != null && !idHex.isEmpty()) {
                    deviceId = idHex;
                }
            }

            log.debug("Extracted coordinates from BSM: lat={}, lon={}, deviceId={}", 
                    latitude, longitude, deviceId);

            return GeoRelevanceMessage.builder()
                    .timestamp(Instant.now())
                    .latitude(latitude)
                    .longitude(longitude)
                    .asn1Binary(uperBytes)
                    .messageType("BSM")
                    .sourceTopic(sourceTopic)
                    .deviceId(deviceId)  // Add device ID to the message
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse XER message: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Failed to decode BSM message: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Decodes a BSM message from hex string format.
     * 
     * @param hexString Hex string representation of UPER bytes
     * @param sourceTopic Original MQTT topic where message was received
     * @return GeoRelevanceMessage with extracted coordinates, or null if decoding fails
     */
    public GeoRelevanceMessage decodeBsmFromHex(String hexString, String sourceTopic) {
        try {
            byte[] bytes = java.util.HexFormat.of().parseHex(hexString);
            return decodeBsm(bytes, sourceTopic);
        } catch (Exception e) {
            log.error("Failed to parse hex string: {}", e.getMessage(), e);
            return null;
        }
    }
}

