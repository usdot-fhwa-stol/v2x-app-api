package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public @Data class RegistrationPendingResponse {
    @JsonProperty("DeviceID")
    private String deviceId;
    @JsonProperty("Message")
    private String message;
}
