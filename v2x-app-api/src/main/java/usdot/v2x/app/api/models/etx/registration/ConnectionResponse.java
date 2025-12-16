package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public @Data class ConnectionResponse {
    @JsonProperty("MqttURL")
    private String mqttUrl;
}
