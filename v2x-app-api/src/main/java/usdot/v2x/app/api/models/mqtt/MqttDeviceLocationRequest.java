package usdot.v2x.app.api.models.mqtt;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request model for registering/updating MQTT device location.
 */
@Data
public class MqttDeviceLocationRequest {

    @NotBlank(message = "MQTT client ID is required")
    private String mqttClientId;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    private Double elevation;

    private Double heading;

    private Double speed;
}



