package usdot.v2x.georouter.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model representing a geo-relevance message wrapper.
 * This can be used for protobuf-style messages with timestamp, lat, lon, and ASN.1 binary.
 * 
 * For now, we'll work directly with the ASN.1 binary and extract coordinates from it,
 * but this model can be extended to support protobuf deserialization if needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoRelevanceMessage {
    /**
     * Timestamp when the message was created/received.
     */
    private Instant timestamp;

    /**
     * Latitude in decimal degrees (extracted from BSM).
     */
    private Double latitude;

    /**
     * Longitude in decimal degrees (extracted from BSM).
     */
    private Double longitude;

    /**
     * Raw ASN.1 UPER encoded message bytes.
     */
    private byte[] asn1Binary;

    /**
     * Message type (e.g., "BSM", "TIM", "MAP").
     */
    private String messageType;

    /**
     * Original MQTT topic where message was received.
     */
    private String sourceTopic;
    
    /**
     * Device/client identifier (extracted from BSM coreData.id).
     */
    private String deviceId;
}


