package usdot.v2x.app.api.models.mqtt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response model for MQTT device location operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttDeviceLocationResponse {
    private String mqttClientId;
    private Double latitude;
    private Double longitude;
    private String geohash;
    private Boolean isActive;
    private Instant lastUpdated;
    private Instant lastConnected;
}



